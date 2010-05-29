/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.fileTemplates.impl.FileTemplateImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
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
import org.apache.velocity.runtime.parser.node.ASTReference;
import org.apache.velocity.runtime.parser.node.Node;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.runtime.resource.loader.FileResourceLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/**
 * @author MYakovlev
 */
public class FileTemplateUtil{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.FileTemplateUtil");
  private static boolean ourVelocityInitialized = false;
  private static final CreateFromTemplateHandler ourDefaultCreateFromTemplateHandler = new DefaultCreateFromTemplateHandler();

  private FileTemplateUtil() {
  }

  public static String[] calculateAttributes(String templateContent, Properties properties, boolean includeDummies) throws ParseException {
    initVelocity();
    final Set<String> unsetAttributes = new HashSet<String>();
    //noinspection HardCodedStringLiteral
    addAttributesToVector(unsetAttributes, RuntimeSingleton.parse(new StringReader(templateContent), "MyTemplate"), properties, includeDummies);
    return ArrayUtil.toStringArray(unsetAttributes);
  }

  private static void addAttributesToVector(Set<String> references, Node apacheNode, Properties properties, boolean includeDummies){
    int childCount = apacheNode.jjtGetNumChildren();
    for(int i = 0; i < childCount; i++){
      Node apacheChild = apacheNode.jjtGetChild(i);
      addAttributesToVector(references, apacheChild, properties, includeDummies);
      if(apacheChild instanceof ASTReference){
        ASTReference apacheReference = (ASTReference)apacheChild;
        String s = apacheReference.literal();
        s = referenceToAttribute(s, includeDummies);
        if (s != null && s.length() > 0 && properties.getProperty(s) == null) references.add(s);
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

  public static String mergeTemplate(Map attributes, String content) throws IOException{
    initVelocity();
    VelocityContext context = new VelocityContext();
    for (final Object o : attributes.keySet()) {
      String name = (String)o;
      context.put(name, attributes.get(name));
    }
    return mergeTemplate(content, context);
  }

  public static String mergeTemplate(Properties attributes, String content) throws IOException{
    initVelocity();
    VelocityContext context = new VelocityContext();
    Enumeration<?> names = attributes.propertyNames();
    while (names.hasMoreElements()){
      String name = (String)names.nextElement();
      context.put(name, attributes.getProperty(name));
    }
    return mergeTemplate(content, context);
  }

  private static String mergeTemplate(String templateContent, final VelocityContext context) throws IOException {
    initVelocity();
    StringWriter stringWriter = new StringWriter();
    try {
      Velocity.evaluate(context, stringWriter, "", templateContent);
    }
    catch (VelocityException e) {
      LOG.error("Error evaluating template:\n"+templateContent,e);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showErrorDialog(IdeBundle.message("error.parsing.file.template"),
                                   IdeBundle.message("title.velocity.error"));
        }
      });
    }
    return stringWriter.toString();
  }

  public static FileTemplate cloneTemplate(FileTemplate template){
    FileTemplateImpl templateImpl = (FileTemplateImpl) template;
    return (FileTemplate)templateImpl.clone();
  }

  public static void copyTemplate(FileTemplate src, FileTemplate dest){
    dest.setExtension(src.getExtension());
    dest.setName(src.getName());
    dest.setText(src.getText());
    dest.setAdjust(src.isAdjust());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static synchronized void initVelocity(){
    try{
      if (ourVelocityInitialized) {
        return;
      }
      File modifiedPatternsPath = new File(PathManager.getConfigPath());
      modifiedPatternsPath = new File(modifiedPatternsPath, "fileTemplates");
      modifiedPatternsPath = new File(modifiedPatternsPath, "includes");

      LogSystem emptyLogSystem = new LogSystem() {
        public void init(RuntimeServices runtimeServices) throws Exception {
        }

        public void logVelocityMessage(int i, String s) {
          //todo[myakovlev] log somethere?
        }
      };
      Velocity.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, emptyLogSystem);
      Velocity.setProperty(RuntimeConstants.RESOURCE_LOADER, "file,class");
      //todo[myakovlev] implement my oun Loader, with ability to load templates from classpath
      Velocity.setProperty("file.resource.loader.class", MyFileResourceLoader.class.getName());
      Velocity.setProperty("class.resource.loader.class", MyClasspathResourceLoader.class.getName());
      Velocity.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, modifiedPatternsPath.getAbsolutePath());
      Velocity.setProperty(RuntimeConstants.INPUT_ENCODING, FileTemplate.ourEncoding);
      Velocity.init();
      ourVelocityInitialized = true;
    }
    catch (Exception e){
      LOG.error("Unable to init Velocity", e);
    }
  }

  public static PsiElement createFromTemplate(@NotNull final FileTemplate template,
                                              @NonNls @Nullable final String fileName,
                                              @Nullable Properties props,
                                              @NotNull final PsiDirectory directory) throws Exception {
    @NotNull final Project project = directory.getProject();
    if (props == null) {
      props = FileTemplateManager.getInstance().getDefaultProperties();
    }
    FileTemplateManager.getInstance().addRecentName(template.getName());
    fillDefaultProperties(props, directory);

    if (fileName != null && props.getProperty(FileTemplate.ATTRIBUTE_NAME) == null) {
      props.setProperty(FileTemplate.ATTRIBUTE_NAME, fileName);
    }

    //Set escaped references to dummy values to remove leading "\" (if not already explicitely set)
    String[] dummyRefs = calculateAttributes(template.getText(), props, true);
    for (String dummyRef : dummyRefs) {
      props.setProperty(dummyRef, "");
    }

    if (template.isJavaClassTemplate()){
      String packageName = props.getProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME);
      if(packageName == null || packageName.length() == 0){
        props = new Properties(props);
        props.setProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME, FileTemplate.ATTRIBUTE_PACKAGE_NAME);
      }
    }
    String mergedText = template.getText(props);
    final String templateText = StringUtil.convertLineSeparators(mergedText);
    final Exception[] commandException = new Exception[1];
    final PsiElement[] result = new PsiElement[1];
    final Properties finalProps = props;
    CommandProcessor.getInstance().executeCommand(project, new Runnable(){
      public void run(){
        final Runnable run = new Runnable(){
          public void run(){
            try{
              CreateFromTemplateHandler handler = findHandler(template);
              result [0] = handler.createFromTemplate(project, directory, fileName, template, templateText, finalProps);
            }
            catch (Exception ex){
              commandException[0] = ex;
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(run);
      }
    }, template.isJavaClassTemplate()
       ? IdeBundle.message("command.create.class.from.template")
       : IdeBundle.message("command.create.file.from.template"), null);
    if(commandException[0] != null){
      throw commandException[0];
    }
    return result[0];
  }

  private static CreateFromTemplateHandler findHandler(final FileTemplate template) {
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

  @NonNls private static final String INCLUDES_PATH = "fileTemplates/includes/";

  public static class MyClasspathResourceLoader extends ClasspathResourceLoader{
    @NonNls private static final String FT_EXTENSION = ".ft";

    public synchronized InputStream getResourceStream(String name) throws ResourceNotFoundException{
      return super.getResourceStream(INCLUDES_PATH + name + FT_EXTENSION);
    }
  }

  public static class MyFileResourceLoader extends FileResourceLoader{
    public void init(ExtendedProperties configuration){
      super.init(configuration);

      File modifiedPatternsPath = new File(PathManager.getConfigPath());
      modifiedPatternsPath = new File(modifiedPatternsPath, INCLUDES_PATH);

      try{
        Field pathsField = FileResourceLoader.class.getDeclaredField("paths");
        pathsField.setAccessible(true);
        Collection<String> paths = (Collection)pathsField.get(this);
        paths.clear();
        paths.add(modifiedPatternsPath.getAbsolutePath());
        if(ApplicationManager.getApplication().isUnitTestMode()){
          File file1 = new File(PathManagerEx.getTestDataPath());
          File testsDir = new File(new File(file1, "ide"), "fileTemplates");
          paths.add(testsDir.getAbsolutePath());
        }
      }
      catch (Exception e){
        throw new RuntimeException(e);
      }
    }
  }
                         
  public static boolean canCreateFromTemplate (PsiDirectory[] dirs, FileTemplate template) {
    FileType fileType = FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(template.getExtension());
    if (fileType.equals(FileTypes.UNKNOWN)) return false;
    CreateFromTemplateHandler handler = findHandler(template);
    return handler.canCreate(dirs);
  }
}
