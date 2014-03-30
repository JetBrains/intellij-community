/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.fileTemplates;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.ArrayUtil;
import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.log.LogSystem;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.Token;
import org.apache.velocity.runtime.parser.node.*;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.loader.ResourceLoader;
import org.apache.velocity.util.StringUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.util.*;

/**
 * @author MYakovlev
 */
public class FileTemplateUtil{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.FileTemplateUtil");
  private static final CreateFromTemplateHandler ourDefaultCreateFromTemplateHandler = new DefaultCreateFromTemplateHandler();

  @NonNls public static final String INTERNAL_PACKAGE_INFO_TEMPLATE_NAME = "package-info";

  private FileTemplateUtil() {
  }

  static {
    try{
      final FileTemplateManager templateManager = FileTemplateManager.getInstance();

      LogSystem emptyLogSystem = new LogSystem() {
        @Override
        public void init(RuntimeServices runtimeServices) throws Exception {
        }

        @Override
        public void logVelocityMessage(int i, String s) {
          //todo[myakovlev] log somethere?
        }
      };
      Velocity.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, emptyLogSystem);
      Velocity.setProperty(RuntimeConstants.INPUT_ENCODING, FileTemplate.ourEncoding);
      Velocity.setProperty(RuntimeConstants.PARSER_POOL_SIZE, 3);
      Velocity.setProperty(RuntimeConstants.RESOURCE_LOADER, "includes");
      Velocity.setProperty("includes.resource.loader.instance", new ResourceLoader() {
        @Override
        public void init(ExtendedProperties configuration) {
        }

        @Override
        public InputStream getResourceStream(String resourceName) throws ResourceNotFoundException {
          final FileTemplate include = templateManager.getPattern(resourceName);
          if (include == null) {
            throw new ResourceNotFoundException("Template not found: " + resourceName);
          }
          final String text = include.getText();
          try {
            return new ByteArrayInputStream(text.getBytes(FileTemplate.ourEncoding));
          }
          catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
          }
        }

        @Override
        public boolean isSourceModified(Resource resource) {
          return true;
        }

        @Override
        public long getLastModified(Resource resource) {
          return 0L;
        }
      });
      Velocity.init();
    }
    catch (Exception e){
      LOG.error("Unable to init Velocity", e);
    }
  }

  public static String[] calculateAttributes(String templateContent, Properties properties, boolean includeDummies) throws ParseException {
    Set<String> propertiesNames = new HashSet<String>();
    for (Enumeration e = properties.propertyNames(); e.hasMoreElements(); ) {
      propertiesNames.add((String)e.nextElement());
    }
    return calculateAttributes(templateContent, propertiesNames, includeDummies);
  }

  public static String[] calculateAttributes(String templateContent, Map<String, Object> properties, boolean includeDummies) throws ParseException {
    return calculateAttributes(templateContent, properties.keySet(), includeDummies);
  }

  public static String[] calculateAttributes(String templateContent, Set<String> propertiesNames, boolean includeDummies) throws ParseException {
    final Set<String> unsetAttributes = new LinkedHashSet<String>();
    final Set<String> definedAttributes = new HashSet<String>();
    //noinspection HardCodedStringLiteral
    SimpleNode template = RuntimeSingleton.parse(new StringReader(templateContent), "MyTemplate");
    collectAttributes(unsetAttributes, definedAttributes, template, propertiesNames, includeDummies, new HashSet<String>());
    for (String definedAttribute : definedAttributes) {
      unsetAttributes.remove(definedAttribute);
    }
    return ArrayUtil.toStringArray(unsetAttributes);
  }

  private static void collectAttributes(Set<String> referenced, Set<String> defined, Node apacheNode, final Set<String> propertiesNames, final boolean includeDummies, Set<String> visitedIncludes)
    throws ParseException {
    int childCount = apacheNode.jjtGetNumChildren();
    for(int i = 0; i < childCount; i++){
      Node apacheChild = apacheNode.jjtGetChild(i);
      collectAttributes(referenced, defined, apacheChild, propertiesNames, includeDummies, visitedIncludes);
      if (apacheChild instanceof ASTReference){
        ASTReference apacheReference = (ASTReference)apacheChild;
        String s = apacheReference.literal();
        s = referenceToAttribute(s, includeDummies);
        if (s != null && s.length() > 0 && !propertiesNames.contains(s)) {
          referenced.add(s);
        }
      }
      else if (apacheChild instanceof ASTSetDirective) {
        ASTReference lhs = (ASTReference) apacheChild.jjtGetChild(0);
        String attr = referenceToAttribute(lhs.literal(), false);
        if (attr != null) {
          defined.add(attr);
        }
      }
      else if (apacheChild instanceof ASTDirective && "parse".equals(((ASTDirective)apacheChild).getDirectiveName()) && apacheChild.jjtGetNumChildren() == 1) {
        Node literal = apacheChild.jjtGetChild(0);
        if (literal instanceof ASTStringLiteral && literal.jjtGetNumChildren() == 0) {
          Token firstToken = literal.getFirstToken();
          if (firstToken != null) {
            String s = StringUtil.unquoteString(firstToken.toString());
            final FileTemplate includedTemplate = FileTemplateManager.getInstance().getTemplate(s);
            if (includedTemplate != null && visitedIncludes.add(s)) {
              SimpleNode template = RuntimeSingleton.parse(new StringReader(includedTemplate.getText()), "MyTemplate");
              collectAttributes(referenced, defined, template, propertiesNames, includeDummies, visitedIncludes);
            }
          }
        }
      }
    }
  }


  /**
   * Removes each two leading '\', removes leading $, removes {}
   * Examples:
   * $qqq   -> qqq
   * \$qqq  -> qqq if dummy attributes are collected too, null otherwise
   * \\$qqq -> qqq
   * ${qqq} -> qqq
   */
  @Nullable
  private static String referenceToAttribute(String attrib, boolean includeDummies) {
    while (attrib.startsWith("\\\\")) {
      attrib = attrib.substring(2);
    }
    if (attrib.startsWith("\\$")) {
      if (includeDummies) {
        attrib = attrib.substring(1);
      }
      else return null;
    }
    if (!StringUtil.startsWithChar(attrib, '$')) {
      return null;
    }
    attrib = attrib.substring(1);
    if (StringUtil.startsWithChar(attrib, '{')) {
      String cleanAttribute = null;
      for (int i = 1; i < attrib.length(); i++) {
        char currChar = attrib.charAt(i);
        if (currChar == '{' || currChar == '.') {
          // Invalid match
          cleanAttribute = null;
          break;
        }
        else if (currChar == '}') {
          // Valid match
          cleanAttribute = attrib.substring(1, i);
          break;
        }
      }
      attrib = cleanAttribute;
    }
    else {
      for (int i = 0; i < attrib.length(); i++) {
        char currChar = attrib.charAt(i);
        if (currChar == '{' || currChar == '}' || currChar == '.') {
          attrib = attrib.substring(0, i);
          break;
        }
      }
    }
    return attrib;
  }

  public static String mergeTemplate(Map attributes, String content, boolean useSystemLineSeparators) throws IOException{
    VelocityContext context = createVelocityContext();
    for (final Object o : attributes.keySet()) {
      String name = (String)o;
      context.put(name, attributes.get(name));
    }
    return mergeTemplate(content, context, useSystemLineSeparators);
  }

  private static VelocityContext createVelocityContext() {
    VelocityContext context = new VelocityContext();
    context.put("StringUtils", StringUtils.class);
    return context;
  }

  public static String mergeTemplate(Properties attributes, String content, boolean useSystemLineSeparators) throws IOException{
    VelocityContext context = createVelocityContext();
    Enumeration<?> names = attributes.propertyNames();
    while (names.hasMoreElements()){
      String name = (String)names.nextElement();
      context.put(name, attributes.getProperty(name));
    }
    return mergeTemplate(content, context, useSystemLineSeparators);
  }

  private static String mergeTemplate(String templateContent, final VelocityContext context, boolean useSystemLineSeparators) throws IOException {
    final StringWriter stringWriter = new StringWriter();
    try {
      Velocity.evaluate(context, stringWriter, "", templateContent);
    }
    catch (final VelocityException e) {
      LOG.error("Error evaluating template:\n"+templateContent,e);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(IdeBundle.message("error.parsing.file.template", e.getMessage()),
                                   IdeBundle.message("title.velocity.error"));
        }
      });
    }
    final String result = stringWriter.toString();

    if (useSystemLineSeparators) {
      final String newSeparator = CodeStyleSettingsManager.getSettings(ProjectManagerEx.getInstanceEx().getDefaultProject()).getLineSeparator();
      if (!"\n".equals(newSeparator)) {
        return StringUtil.convertLineSeparators(result, newSeparator);
      }
    }

    return result;
  }

  public static PsiElement createFromTemplate(@NotNull final FileTemplate template,
                                              @NonNls @Nullable final String fileName,
                                              @Nullable Properties props,
                                              @NotNull final PsiDirectory directory) throws Exception {
    Map<String, Object> map;
    if (props != null) {
      map = new HashMap<String, Object>();
      putAll(map, props);
    }
    else {
      map = null;
    }
    return createFromTemplate(template, fileName, map, directory, null);
  }

  public static PsiElement createFromTemplate(@NotNull final FileTemplate template,
                                              @NonNls @Nullable String fileName,
                                              @Nullable Properties props,
                                              @NotNull final PsiDirectory directory,
                                              @Nullable ClassLoader classLoader) throws Exception {
    Map<String, Object> map;
    if (props != null) {
      map = new HashMap<String, Object>();
      putAll(map, props);
    }
    else {
      map = null;
    }
    return createFromTemplate(template, fileName, map, directory, classLoader);
  }

  public static PsiElement createFromTemplate(@NotNull final FileTemplate template,
                                              @NonNls @Nullable String fileName,
                                              @Nullable Map<String, Object> propsMap,
                                              @NotNull final PsiDirectory directory,
                                              @Nullable ClassLoader classLoader) throws Exception {
    @NotNull final Project project = directory.getProject();
    if (propsMap == null) {
      Properties p = FileTemplateManager.getInstance().getDefaultProperties(directory.getProject());
      propsMap = new HashMap<String, Object>();
      putAll(propsMap, p);
    }
    FileTemplateManager.getInstance().addRecentName(template.getName());
    Properties p = new Properties();
    fillDefaultProperties(p, directory);
    putAll(propsMap, p);

    final CreateFromTemplateHandler handler = findHandler(template);
    if (fileName != null && propsMap.get(FileTemplate.ATTRIBUTE_NAME) == null) {
      propsMap.put(FileTemplate.ATTRIBUTE_NAME, fileName);
    }
    else if (fileName == null && handler.isNameRequired()) {
      fileName = (String)propsMap.get(FileTemplate.ATTRIBUTE_NAME);
      if (fileName == null) {
        throw new Exception("File name must be specified");
      }
    }

    //Set escaped references to dummy values to remove leading "\" (if not already explicitly set)
    String[] dummyRefs = calculateAttributes(template.getText(), propsMap, true);
    for (String dummyRef : dummyRefs) {
      propsMap.put(dummyRef, "");
    }

    handler.prepareProperties(propsMap);

    final Map<String, Object> props_ = propsMap;
    final String fileName_ = fileName;
    String mergedText = ClassLoaderUtil.runWithClassLoader(classLoader != null ? classLoader : FileTemplateUtil.class.getClassLoader(),
                                                           new ThrowableComputable<String, IOException>() {
                                                             @Override
                                                             public String compute() throws IOException {
                                                               return template.getText(props_);
                                                             }
                                                           });
    final String templateText = StringUtil.convertLineSeparators(mergedText);
    final Exception[] commandException = new Exception[1];
    final PsiElement[] result = new PsiElement[1];
    CommandProcessor.getInstance().executeCommand(project, new Runnable(){
      @Override
      public void run(){
        ApplicationManager.getApplication().runWriteAction(new Runnable(){
          @Override
          public void run(){
            try{
              result[0] = handler.createFromTemplate(project, directory, fileName_, template, templateText, props_);
            }
            catch (Exception ex){
              commandException[0] = ex;
            }
          }
        });
      }
    }, template.isTemplateOfType(StdFileTypes.JAVA) && !"package-info".equals(template.getName())
       ? IdeBundle.message("command.create.class.from.template")
       : IdeBundle.message("command.create.file.from.template"), null);
    if(commandException[0] != null){
      throw commandException[0];
    }
    return result[0];
  }

  public static CreateFromTemplateHandler findHandler(final FileTemplate template) {
    for(CreateFromTemplateHandler handler: Extensions.getExtensions(CreateFromTemplateHandler.EP_NAME)) {
      if (handler.handlesTemplate(template)) {
        return handler;
      }
    }
    return ourDefaultCreateFromTemplateHandler;
  }

  public static void fillDefaultProperties(final Properties props, final PsiDirectory directory) {
    final DefaultTemplatePropertiesProvider[] providers = Extensions.getExtensions(DefaultTemplatePropertiesProvider.EP_NAME);
    for(DefaultTemplatePropertiesProvider provider: providers) {
      provider.fillProperties(directory, props);
    }
  }

  public static String indent(String methodText, Project project, FileType fileType) {
    int indent = CodeStyleSettingsManager.getSettings(project).getIndentSize(fileType);
    return methodText.replaceAll("\n", "\n" + StringUtil.repeatSymbol(' ',indent));
  }


  public static boolean canCreateFromTemplate (PsiDirectory[] dirs, FileTemplate template) {
    FileType fileType = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(template.getExtension());
    if (fileType.equals(FileTypes.UNKNOWN)) return false;
    CreateFromTemplateHandler handler = findHandler(template);
    return handler.canCreate(dirs);
  }

  @Nullable
  public static Icon getIcon(@NotNull FileTemplate fileTemplate) {
    return FileTypeManager.getInstance().getFileTypeByExtension(fileTemplate.getExtension()).getIcon();
  }

  public static void putAll(final Map<String, Object> props, final Properties p) {
    for (Enumeration<?> e = p.propertyNames(); e.hasMoreElements();) {
      String s = (String)e.nextElement();
      props.put(s, p.getProperty(s));
    }
  }
}
