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
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.documentation.DocumentationManagerUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.source.javadoc.PsiInlineDocTagImpl;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

public class JavaDocInfoGenerator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.javadoc.JavaDocInfoGenerator");

  @NonNls private static final Pattern ourNotDot      = Pattern.compile("[^.]");
  @NonNls private static final Pattern ourWhitespaces = Pattern.compile("[ \\n\\r\\t]+");

  @NonNls private static final String THROWS_KEYWORD = "throws";
  @NonNls private static final String BR_TAG         = "<br>";
  @NonNls private static final String LINK_TAG       = "link";
  @NonNls private static final String LITERAL_TAG    = "literal";
  @NonNls private static final String CODE_TAG       = "code";
  @NonNls private static final String LINKPLAIN_TAG  = "linkplain";
  @NonNls private static final String INHERITDOC_TAG = "inheritDoc";
  @NonNls private static final String DOCROOT_TAG    = "docRoot";
  @NonNls private static final String VALUE_TAG      = "value";
  
  private final Project    myProject;
  private final PsiElement myElement;
  
  interface InheritDocProvider<T> {
    Pair<T, InheritDocProvider<T>> getInheritDoc();

    PsiClass getElement();
  }

  private static final InheritDocProvider<PsiDocTag> ourEmptyProvider = new InheritDocProvider<PsiDocTag>() {
    @Override
    public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
      return null;
    }

    @Override
    public PsiClass getElement() {
      return null;
    }
  };

  private static final InheritDocProvider<PsiElement[]> ourEmptyElementsProvider = mapProvider(ourEmptyProvider, false);

  private static InheritDocProvider<PsiElement[]> mapProvider(final InheritDocProvider<PsiDocTag> i,
                                                              final boolean dropFirst)
  {
    return new InheritDocProvider<PsiElement[]>() {
      @Override
      public Pair<PsiElement[], InheritDocProvider<PsiElement[]>> getInheritDoc() {
        Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = i.getInheritDoc();

        if (pair == null) {
          return null;
        }

        PsiElement[] elements;
        PsiElement[] rawElements = pair.first.getDataElements();

        if (dropFirst && rawElements != null && rawElements.length > 0) {
          elements = new PsiElement[rawElements.length - 1];

          System.arraycopy(rawElements, 1, elements, 0, elements.length);
        }
        else {
          elements = rawElements;
        }

        return new Pair<PsiElement[], InheritDocProvider<PsiElement[]>>(elements, mapProvider(pair.second, dropFirst));
      }

      @Override
      public PsiClass getElement() {
        return i.getElement();
      }
    };
  }

  interface DocTagLocator <T> {
    T find(PsiDocComment comment);
  }

  private static DocTagLocator<PsiDocTag> parameterLocator(final String name) {
    return new DocTagLocator<PsiDocTag>() {
      @Override
      public PsiDocTag find(PsiDocComment comment) {
        if (comment == null) {
          return null;
        }

        PsiDocTag[] tags = comment.findTagsByName("param");

        for (PsiDocTag tag : tags) {
          PsiDocTagValue value = tag.getValueElement();

          if (value != null) {
            String text = value.getText();

            if (text != null && text.equals(name)) {
              return tag;
            }
          }
        }

        return null;
      }
    };
  }

  private static DocTagLocator<PsiDocTag> exceptionLocator(final String name) {
    return new DocTagLocator<PsiDocTag>() {
      @Override
      public PsiDocTag find(PsiDocComment comment) {
        if (comment == null) {
          return null;
        }

        PsiDocTag[] tags = getThrowsTags(comment);

        for (PsiDocTag tag : tags) {
          PsiDocTagValue value = tag.getValueElement();

          if (value != null) {
            String text = value.getText();

            if (text != null && areWeakEqual(text, name)) {
              return tag;
            }
          }
        }

        return null;
      }
    };
  }

  public JavaDocInfoGenerator(Project project, PsiElement element) {
    myProject = project;
    myElement = element;
  }

  @Nullable
  public String generateFileInfo() {
    StringBuilder buffer = new StringBuilder();
    if (myElement instanceof PsiFile) {
      generateFileJavaDoc(buffer, (PsiFile)myElement, true); //used for Ctrl-Click
    }

    return fixupDoc(buffer);
  }

  @Nullable
  private static String fixupDoc(@NotNull final StringBuilder buffer) {
    String text = buffer.toString();
    if (text.isEmpty()) {
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Generated JavaDoc:");
      LOG.debug(text);
    }

    return StringUtil.replace(text, "/>", ">");
  }

  public boolean generateDocInfoCore (final StringBuilder buffer, final boolean generatePrologueAndEpilogue) {
    if (myElement instanceof PsiClass) {
      generateClassJavaDoc(buffer, (PsiClass)myElement, generatePrologueAndEpilogue);
    }
    else if (myElement instanceof PsiMethod) {
      generateMethodJavaDoc(buffer, (PsiMethod)myElement, generatePrologueAndEpilogue);
    } else if (myElement instanceof PsiParameter) {
      generateMethodParameterJavaDoc(buffer, (PsiParameter)myElement, generatePrologueAndEpilogue);
    }
    else if (myElement instanceof PsiField) {
      generateFieldJavaDoc(buffer, (PsiField)myElement, generatePrologueAndEpilogue);
    }
    else if (myElement instanceof PsiVariable) {
      generateVariableJavaDoc(buffer, (PsiVariable)myElement, generatePrologueAndEpilogue);
    }
    else if (myElement instanceof PsiDirectory) {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)myElement);
      if (aPackage == null)
        return false;
      generatePackageJavaDoc(buffer, aPackage, generatePrologueAndEpilogue);
    }
    else if (myElement instanceof PsiPackage) {
      generatePackageJavaDoc(buffer, (PsiPackage) myElement, generatePrologueAndEpilogue);
    } else {
      return false;
    }

    return true;
  }

  @Nullable
  public String generateDocInfo(List<String> docURLs) {
    StringBuilder buffer = new StringBuilder();

    if (!generateDocInfoCore(buffer, true))
      return null;
    
    if (docURLs != null) {
      if (buffer.length() == 0) {
        buffer.append("<html><body></body></html>");
      }
      String errorSection = "<p id=\"error\">Following external urls were checked:<br>&nbsp;&nbsp;&nbsp;<i>" +
                   StringUtil.join(docURLs, "</i><br>&nbsp;&nbsp;&nbsp;<i>") +
                   "</i><br>The documentation for this element is not found. Please add all the needed paths to API docs in " +
                   "<a href=\"open://Project Settings\">Project Settings.</a></p>";
      buffer.insert(buffer.indexOf("<body>"), errorSection);
    }
    return fixupDoc(buffer);
  }

  private void generateClassJavaDoc(@NonNls StringBuilder buffer, PsiClass aClass, boolean generatePrologueAndEpilogue) {
    if (aClass instanceof PsiAnonymousClass) return;
    PsiManager manager = aClass.getManager();
    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);

    PsiFile file = aClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile)file).getPackageName();
      if (!packageName.isEmpty()) {
        buffer.append("<small><b>");
        buffer.append(packageName);
        buffer.append("</b></small>");
        //buffer.append("<br>");
      }
    }

    buffer.append("<PRE>");
    generateAnnotations(buffer, aClass);
    String modifiers = PsiFormatUtil.formatModifiers(aClass, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    if (!modifiers.isEmpty()) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
    buffer.append(aClass.isInterface() ? LangBundle.message("java.terms.interface") : LangBundle.message("java.terms.class"));
    buffer.append(" ");
    String refText = JavaDocUtil.getReferenceText(myProject, aClass);
    if (refText == null) {
      buffer.setLength(0);
      return;
    }
    String labelText = JavaDocUtil.getLabelText(myProject, manager, refText, aClass);
    buffer.append("<b>");
    buffer.append(labelText);
    buffer.append("</b>");

    buffer.append(generateTypeParameters(aClass));

    buffer.append("\n");

    PsiClassType[] refs = aClass.getExtendsListTypes();

    String qName = aClass.getQualifiedName();

    if (refs.length > 0 || !aClass.isInterface() && (qName == null || !qName.equals(CommonClassNames.JAVA_LANG_OBJECT))) {
      buffer.append("extends ");
      if (refs.length == 0) {
        generateLink(buffer, CommonClassNames.JAVA_LANG_OBJECT, null, aClass, false);
      }
      else {
        for (int i = 0; i < refs.length; i++) {
          generateType(buffer, refs[i], aClass);
          if (i < refs.length - 1) {
            buffer.append(",&nbsp;");
          }
        }
      }
      buffer.append("\n");
    }

    refs = aClass.getImplementsListTypes();

    if (refs.length > 0) {
      buffer.append("implements ");
      for (int i = 0; i < refs.length; i++) {
        generateType(buffer, refs[i], aClass);
        if (i < refs.length - 1) {
          buffer.append(",&nbsp;");
        }
      }
      buffer.append("\n");
    }
    if (buffer.charAt(buffer.length() - 1) == '\n') {
      buffer.setLength(buffer.length() - 1);
    }
    buffer.append("</PRE>");
    //buffer.append("<br>");

    PsiDocComment comment = getDocComment(aClass);
    if (comment != null) {
      generateCommonSection(buffer, comment);
      generateTypeParametersSection(buffer, aClass);
    }

    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  private void generateTypeParametersSection(final StringBuilder buffer, final PsiClass aClass) {
    final PsiDocComment docComment = aClass.getDocComment();
    if (docComment == null) return;
    final LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>> result =
      new LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>>();
    final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
    for (PsiTypeParameter typeParameter : typeParameters) {
      final DocTagLocator<PsiDocTag> locator = parameterLocator("<" + typeParameter.getName() + ">");
      final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> inClassComment = findInClassComment(aClass, locator);
      if (inClassComment != null) {
        result.add(inClassComment);
      }
      else {
        final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = findInHierarchy(aClass, locator);
        if (pair != null) {
          result.add(pair);
        }
      }
    }
    generateTypeParametersSection(buffer, result);
  }

  @Nullable
  private static Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> findInHierarchy(PsiClass psiClass, final DocTagLocator<PsiDocTag> locator) {
    for (final PsiClass superClass : psiClass.getSupers()) {
      final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = findInClassComment(superClass, locator);
      if (pair != null) return pair;
    }
    for (PsiClass superInterface : psiClass.getInterfaces()) {
      final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = findInClassComment(superInterface, locator);
      if (pair != null) return pair;
    }
    return null;
  }

  private static Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> findInClassComment(final PsiClass psiClass, final DocTagLocator<PsiDocTag> locator) {
    final PsiDocTag tag = locator.find(getDocComment(psiClass));
    if (tag != null) {
      return new Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>(tag, new InheritDocProvider<PsiDocTag>() {
        @Override
        public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
          return findInHierarchy(psiClass, locator);
        }

        @Override
        public PsiClass getElement() {
          return psiClass;
        }
      });
    }
    return null;
  }

  @Nullable
  private static PsiDocComment getDocComment(final PsiDocCommentOwner docOwner) {
    PsiElement navElement = docOwner.getNavigationElement();
    if (!(navElement instanceof PsiDocCommentOwner)) {
      LOG.info("Wrong navElement: " + navElement + "; original = " + docOwner + " of class " + docOwner.getClass());
      return null;
    }
    PsiDocComment comment = ((PsiDocCommentOwner)navElement).getDocComment();
    if (comment == null) { //check for non-normalized fields
      final PsiModifierList modifierList = docOwner.getModifierList();
      if (modifierList != null) {
        final PsiElement parent = modifierList.getParent();
        if (parent instanceof PsiDocCommentOwner && parent.getNavigationElement() instanceof PsiDocCommentOwner) {
          return ((PsiDocCommentOwner)parent.getNavigationElement()).getDocComment();
        }
      }
    }
    return comment;
  }

  private void generateFieldJavaDoc(@NonNls StringBuilder buffer, PsiField field, boolean generatePrologueAndEpilogue) {
    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);

    PsiClass parentClass = field.getContainingClass();
    if (parentClass != null) {
      String qName = parentClass.getQualifiedName();
      if (qName != null) {
        buffer.append("<small><b>");
        //buffer.append(qName);
        generateLink(buffer, qName, qName, field, false);
        buffer.append("</b></small>");
        //buffer.append("<br>");
      }
    }

    buffer.append("<PRE>");
    generateAnnotations(buffer, field);
    String modifiers = PsiFormatUtil.formatModifiers(field, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    if (!modifiers.isEmpty()) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
    generateType(buffer, field.getType(), field);
    buffer.append(" ");
    buffer.append("<b>");
    buffer.append(field.getName());
    appendInitializer(buffer, field);
    buffer.append("</b>");
    buffer.append("</PRE>");
    //buffer.append("<br>");

    ColorUtil.appendColorPreview(field, buffer);

    PsiDocComment comment = getDocComment(field);
    if (comment != null) {
      generateCommonSection(buffer, comment);
    }

    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  public static void enumConstantOrdinal(StringBuilder buffer, PsiField field, PsiClass parentClass, final String newLine) {
    if (parentClass != null && field instanceof PsiEnumConstant) {
      final PsiField[] fields = parentClass.getFields();
      final int idx = ArrayUtilRt.find(fields, field);
      if (idx >= 0) {
        buffer.append(newLine);
        buffer.append("Enum constant ordinal: ").append(idx);
      }
    }
  }

  // not a javadoc in fact..
  private static void generateVariableJavaDoc(@NonNls StringBuilder buffer, PsiVariable variable, boolean generatePrologueAndEpilogue) {
    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);

    buffer.append("<PRE>");
    String modifiers = PsiFormatUtil.formatModifiers(variable, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    if (!modifiers.isEmpty()) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
    generateType(buffer, variable.getType(), variable);
    buffer.append(" ");
    buffer.append("<b>");
    buffer.append(variable.getName());
    appendInitializer(buffer, variable);
    buffer.append("</b>");
    buffer.append("</PRE>");
    //buffer.append("<br>");

    ColorUtil.appendColorPreview(variable, buffer);

    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  // not a javadoc in fact..
  private static void generateFileJavaDoc(StringBuilder buffer, PsiFile file, boolean generatePrologueAndEpilogue) {
    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      buffer.append(virtualFile.getPresentableUrl());
    }
    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  private void generatePackageJavaDoc(final StringBuilder buffer, final PsiPackage psiPackage, boolean generatePrologueAndEpilogue) {
    for(PsiDirectory directory: psiPackage.getDirectories()) {
      final PsiFile packageInfoFile = directory.findFile(PsiPackage.PACKAGE_INFO_FILE);
      if (packageInfoFile != null) {
        final ASTNode node = packageInfoFile.getNode();
        if (node != null) {
          final ASTNode docCommentNode = node.findChildByType(JavaDocElementType.DOC_COMMENT);
          if (docCommentNode != null) {
            final PsiDocComment docComment = (PsiDocComment)docCommentNode.getPsi();

            if (generatePrologueAndEpilogue)
              generatePrologue(buffer);

            generateCommonSection(buffer, docComment);

            if (generatePrologueAndEpilogue)
              generateEpilogue(buffer);
            break;
          }
        }
      }
      PsiFile packageHtmlFile = directory.findFile("package.html");
      if (packageHtmlFile != null) {
        generatePackageHtmlJavaDoc(buffer, packageHtmlFile, generatePrologueAndEpilogue);
        break;
      }
    }
  }

  public void generateCommonSection(StringBuilder buffer, PsiDocComment docComment) {
    generateDescription(buffer, docComment);
    generateDeprecatedSection(buffer, docComment);
    generateSinceSection(buffer, docComment);
    generateSeeAlsoSection(buffer, docComment);
  }

  private void generatePackageHtmlJavaDoc(final StringBuilder buffer, final PsiFile packageHtmlFile, boolean generatePrologueAndEpilogue) {
    String htmlText = packageHtmlFile.getText();

    try {
      final Document document = JDOMUtil.loadDocument(new ByteArrayInputStream(htmlText.getBytes(CharsetToolkit.UTF8_CHARSET)));
      final Element rootTag = document.getRootElement();
      if (rootTag != null) {
        final Element subTag = rootTag.getChild("body");
        if (subTag != null) {
          htmlText = subTag.getValue();
        }
      }
    }
    catch (JDOMException ignore) {}
    catch (IOException ignore) {}

    htmlText = StringUtil.replace(htmlText, "*/", "&#42;&#47;");

    final String fileText = "/** " + htmlText + " */";
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(packageHtmlFile.getProject()).getElementFactory();
    final PsiDocComment docComment;
    try {
      docComment = elementFactory.createDocCommentFromText(fileText);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);

    generateCommonSection(buffer, docComment);

    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  private static void appendInitializer(StringBuilder buffer, PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      buffer.append(" = ");

      String text = initializer.getText();
      text = text.trim();
      int index1 = text.indexOf('\n');
      if (index1 < 0) index1 = text.length();
      int index2 = text.indexOf('\r');
      if (index2 < 0) index2 = text.length();
      int index = Math.min(index1, index2);
      boolean trunc = index < text.length();
      if (trunc) {
        text = text.substring(0, index);
        buffer.append(StringUtil.escapeXml(text));
        buffer.append("...");
      }
      else {
        initializer.accept(new MyVisitor(buffer));
      }
    }
  }

  private static void generateAnnotations(@NonNls @NotNull StringBuilder buffer, @NotNull PsiModifierListOwner owner) {
    final PsiModifierList ownerModifierList = owner.getModifierList();
    if (ownerModifierList == null) return;
    PsiAnnotation[] annotations = ownerModifierList.getAnnotations();
    final PsiAnnotation[] externalAnnotations = ExternalAnnotationsManager.getInstance(owner.getProject()).findExternalAnnotations(owner);
    if (externalAnnotations != null) {
      annotations = ArrayUtil.mergeArrays(annotations, externalAnnotations, PsiAnnotation.ARRAY_FACTORY);
    }
    PsiManager manager = owner.getManager();

    for (PsiAnnotation annotation : annotations) {
      final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
      if (nameReferenceElement == null) continue;
      final PsiElement resolved = nameReferenceElement.resolve();
      if (resolved instanceof PsiClass) {
        final PsiClass annotationType = (PsiClass)resolved;
        if (AnnotationUtil.isAnnotated(annotationType, "java.lang.annotation.Documented", false)) {
          final PsiClassType type = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(annotationType, PsiSubstitutor.EMPTY);
          buffer.append("@");
          generateType(buffer, type, owner);
          final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
          if (attributes.length > 0) {
            buffer.append("(");
            boolean first = true;
            for (PsiNameValuePair pair : attributes) {
              if (!first) buffer.append("&nbsp;");
              first = false;
              final String name = pair.getName();
              if (name != null) {
                buffer.append(name);
                buffer.append(" = ");
              }
              final PsiAnnotationMemberValue value = pair.getValue();
              if (value != null) {
                buffer.append(value.getText());
              }
            }
            buffer.append(")");
          }
          buffer.append("&nbsp;");
        }
      } else {
        buffer.append("<font color=red>");
        buffer.append(annotation.getText());
        buffer.append("</font>");
        buffer.append("&nbsp;");
      }
    }
  }

  private void generateMethodParameterJavaDoc(@NonNls StringBuilder buffer, PsiParameter parameter, boolean generatePrologueAndEpilogue) {
    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);

    buffer.append("<PRE>");
    String modifiers = PsiFormatUtil.formatModifiers(parameter, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    if (!modifiers.isEmpty()) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
    generateAnnotations(buffer, parameter);
    generateType(buffer, parameter.getType(), parameter);
    buffer.append(" ");
    buffer.append("<b>");
    buffer.append(parameter.getName());
    appendInitializer(buffer, parameter);
    buffer.append("</b>");
    buffer.append("</PRE>");

    final PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);

    if (method != null) {
      final PsiDocComment docComment = getDocComment(method);
      if (docComment != null) {
        final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tagInfoProvider =
          findDocTag(docComment.getTags(), parameter.getName(), method);

        if (tagInfoProvider != null) {
          PsiElement[] elements = tagInfoProvider.first.getDataElements();
          if (elements.length != 0) generateOneParameter(elements, buffer, tagInfoProvider);
        }
      }
    }

    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  private void generateMethodJavaDoc(@NonNls StringBuilder buffer, PsiMethod method, boolean generatePrologueAndEpilogue) {
    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);

    PsiClass parentClass = method.getContainingClass();
    if (parentClass != null) {
      String qName = parentClass.getQualifiedName();
      if (qName != null) {
        buffer.append("<small><b>");
        generateLink(buffer, qName, qName, method, false);
        //buffer.append(qName);
        buffer.append("</b></small>");
        //buffer.append("<br>");
      }
    }

    buffer.append("<PRE>");
    generateAnnotations(buffer, method);
    String modifiers = PsiFormatUtil.formatModifiers(method, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    int indent = 0;
    if (!modifiers.isEmpty()) {
      buffer.append(modifiers);
      buffer.append("&nbsp;");
      indent += modifiers.length() + 1;
    }

    final String typeParamsString = generateTypeParameters(method);
    indent += typeParamsString.length();
    if (!typeParamsString.isEmpty()) {
      buffer.append(typeParamsString);
      buffer.append("&nbsp;");
      indent++;
    }

    if (method.getReturnType() != null) {
      indent += generateType(buffer, method.getReturnType(), method);
      buffer.append("&nbsp;");
      indent++;
    }
    buffer.append("<b>");
    String name = method.getName();
    buffer.append(name);
    buffer.append("</b>");
    indent += name.length();

    buffer.append("(");

    PsiParameter[] parms = method.getParameterList().getParameters();
    for (int i = 0; i < parms.length; i++) {
      PsiParameter parm = parms[i];
      generateAnnotations(buffer, parm);
      generateType(buffer, parm.getType(), method);
      buffer.append("&nbsp;");
      if (parm.getName() != null) {
        buffer.append(parm.getName());
      }
      if (i < parms.length - 1) {
        buffer.append(",\n ");
        for (int j = 0; j < indent; j++) {
          buffer.append(" ");
        }
      }
    }
    buffer.append(")");

    PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
    if (refs.length > 0) {
      buffer.append("\n");
      indent -= THROWS_KEYWORD.length() + 1;
      for (int i = 0; i < indent; i++) {
        buffer.append(" ");
      }
      indent += THROWS_KEYWORD.length() + 1;
      buffer.append(THROWS_KEYWORD);
      buffer.append("&nbsp;");
      for (int i = 0; i < refs.length; i++) {
        generateLink(buffer, refs[i].getCanonicalText(), null, method, false);
        if (i < refs.length - 1) {
          buffer.append(",\n");
          for (int j = 0; j < indent; j++) {
            buffer.append(" ");
          }
        }
      }
    }

    buffer.append("</PRE>");
    //buffer.append("<br>");

    PsiDocComment comment = getMethodDocComment(method);

    generateMethodDescription(buffer, method, comment);

    generateSuperMethodsSection(buffer, method, false);
    generateSuperMethodsSection(buffer, method, true);

    if (comment != null) {
      generateDeprecatedSection(buffer, comment);
    }

    generateParametersSection(buffer, method, comment);
    generateTypeParametersSection(buffer, method);
    generateReturnsSection(buffer, method, comment);
    generateThrowsSection(buffer, method, comment);

    if (comment != null) {
      generateSinceSection(buffer, comment);
      generateSeeAlsoSection(buffer, comment);
    }

    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private PsiDocComment getMethodDocComment(final PsiMethod method) {
    final PsiClass parentClass = method.getContainingClass();
    if (parentClass != null && parentClass.isEnum()) {
      final PsiParameterList parameterList = method.getParameterList();
      if (method.getName().equals("values") && parameterList.getParametersCount() == 0) {
        return loadSyntheticDocComment(method, "/javadoc/EnumValues.java.template");
      }
      if (method.getName().equals("valueOf") && parameterList.getParametersCount() == 1) {
        final PsiType psiType = parameterList.getParameters()[0].getType();
        if (psiType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          return loadSyntheticDocComment(method, "/javadoc/EnumValueOf.java.template");
        }
      }
    }
    return getDocComment(method);
  }

  private PsiDocComment loadSyntheticDocComment(final PsiMethod method, final String resourceName) {
    final InputStream commentStream = JavaDocInfoGenerator.class.getResourceAsStream(resourceName);
    if (commentStream == null) {
      return null;
    }

    final StringBuilder buffer;
    try {
      buffer = new StringBuilder();
      try {
        for (int ch; (ch = commentStream.read()) != -1;) {
          buffer.append((char)ch);
        }
      }
      catch (IOException e) {
        return null;
      }
    }
    finally {
      try {
        commentStream.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    String s = buffer.toString();
    s = StringUtil.replace(s, "<ClassName>", method.getContainingClass().getName());
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    try {
      return elementFactory.createDocCommentFromText(s);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void generatePrologue(StringBuilder buffer) {
    buffer.append("<html><head>" +
                  "    <style type=\"text/css\">" +
                  "        #error {" +
                  "            background-color: #eeeeee;" +
                  "            margin-bottom: 10px;" +
                  "        }" +
                  "        p {" +
                  "            margin: 5px 0;" +
                  "        }" +
                  "    </style>" +
                  "</head><body>");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void generateEpilogue(StringBuilder buffer) {
    while (true) {
      if (buffer.length() < BR_TAG.length()) break;
      char c = buffer.charAt(buffer.length() - 1);
      if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
        buffer.setLength(buffer.length() - 1);
        continue;
      }
      String tail = buffer.substring(buffer.length() - BR_TAG.length());
      if (tail.equalsIgnoreCase(BR_TAG)) {
        buffer.setLength(buffer.length() - BR_TAG.length());
        continue;
      }
      break;
    }
    buffer.append("</body></html>");
  }

  private void generateDescription(StringBuilder buffer, PsiDocComment comment) {
    PsiElement[] elements = comment.getDescriptionElements();
    generateValue(buffer, elements, ourEmptyElementsProvider);
  }

  private static boolean isEmptyDescription(PsiDocComment comment) {
    if (comment == null) {
      return true;
    }
    PsiElement[] descriptionElements = comment.getDescriptionElements();

    for (PsiElement description : descriptionElements) {
      String text = description.getText();

      if (text != null) {
        if (!ourWhitespaces.matcher(text).replaceAll("").isEmpty()) {
          return false;
        }
      }
    }

    return true;
  }

  private void generateMethodDescription(@NonNls StringBuilder buffer, final PsiMethod method, final PsiDocComment comment) {
    final DocTagLocator<PsiElement[]> descriptionLocator = new DocTagLocator<PsiElement[]>() {
      @Override
      public PsiElement[] find(PsiDocComment comment) {
        if (comment == null) {
          return null;
        }

        if (isEmptyDescription(comment)) {
          return null;
        }

        return comment.getDescriptionElements();
      }
    };

    if (comment != null) {
      if (!isEmptyDescription(comment)) {
        generateValue
          (buffer, comment.getDescriptionElements(),
           new InheritDocProvider<PsiElement[]>() {
             @Override
             public Pair<PsiElement[], InheritDocProvider<PsiElement[]>> getInheritDoc() {
               return findInheritDocTag(method, descriptionLocator);
             }

             @Override
             public PsiClass getElement() {
               return method.getContainingClass();
             }
           });
        return;
      }
    }

    Pair<PsiElement[], InheritDocProvider<PsiElement[]>> pair = findInheritDocTag(method, descriptionLocator);

    if (pair != null) {
      PsiElement[] elements = pair.first;
      PsiClass extendee = pair.second.getElement();

      if (elements != null) {
        buffer.append("<DD><DL>");
        buffer.append("<DT><b>");
        buffer.append(extendee.isInterface() ?
                      CodeInsightBundle.message("javadoc.description.copied.from.interface") :
                      CodeInsightBundle .message("javadoc.description.copied.from.class"));
        buffer.append("</b>&nbsp;");
        generateLink(buffer, extendee, JavaDocUtil.getShortestClassName(extendee, method), false);
        buffer.append(BR_TAG);
        generateValue(buffer, elements, pair.second);
        buffer.append("</DD></DL></DD>");
      }
    }
  }

  private void generateValue(StringBuilder buffer, PsiElement[] elements, InheritDocProvider<PsiElement[]> provider) {
    generateValue(buffer, elements, 0, provider);
  }

  private String getDocRoot() {
    PsiClass aClass;

    if (myElement instanceof PsiClass) {
      aClass = (PsiClass)myElement;
    }
    else if (myElement instanceof PsiMember) {
      aClass = ((PsiMember)myElement).getContainingClass();
    }
    else {
      aClass = PsiTreeUtil.getParentOfType(myElement, PsiClass.class);
    }

    if (aClass == null) {
      return "";
    }
    
    String qName = aClass.getQualifiedName();
    if (qName == null) {
      return "";
    }

    return "../" + ourNotDot.matcher(qName).replaceAll("").replaceAll(".", "../");
  }

  private void generateValue(StringBuilder buffer,
                             PsiElement[] elements,
                             int startIndex,
                             InheritDocProvider<PsiElement[]> provider) {
    int predictOffset =
      startIndex < elements.length ?
      elements[startIndex].getTextOffset() + elements[startIndex].getText().length() :
      0;

    for (int i = startIndex; i < elements.length; i++) {
      if (elements[i].getTextOffset() > predictOffset) buffer.append(" ");
      predictOffset = elements[i].getTextOffset() + elements[i].getText().length();
      PsiElement element = elements[i];
      if (element instanceof PsiInlineDocTag) {
        PsiInlineDocTag tag = (PsiInlineDocTag)element;
        final String tagName = tag.getName();
        if (tagName.equals(LINK_TAG)) {
          generateLinkValue(tag, buffer, false);
        }
        else if (tagName.equals(LITERAL_TAG)) {
          generateLiteralValue(buffer, ((PsiInlineDocTagImpl)tag).getDataElementsIgnoreWhitespaces());
        }
        else if (tagName.equals(CODE_TAG)) {
          generateCodeValue(tag, buffer);
        }
        else if (tagName.equals(LINKPLAIN_TAG)) {
          generateLinkValue(tag, buffer, true);
        }
        else if (tagName.equals(INHERITDOC_TAG)) {
          Pair<PsiElement[], InheritDocProvider<PsiElement[]>> inheritInfo = provider.getInheritDoc();

          if (inheritInfo != null) {
            generateValue(buffer, inheritInfo.first, inheritInfo.second);
          }
        }
        else if (tagName.equals(DOCROOT_TAG)) {
          buffer.append(getDocRoot());
        }
        else if (tagName.equals(VALUE_TAG)) {
          generateValueValue(tag, buffer, element);
        }
        else {
          buffer.append(element.getText());
        }
      }
      else {
        buffer.append(element.getText());
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void generateCodeValue(PsiInlineDocTag tag, StringBuilder buffer) {
    buffer.append("<code>");
    generateLiteralValue(buffer, tag.getDataElements());
    buffer.append("</code>");
  }

  private static void generateLiteralValue(StringBuilder buffer, final PsiElement[] dataElements) {
    for (PsiElement element : dataElements) {
      appendPlainText(element.getText(), buffer);
    }
  }

  private static void appendPlainText(@NonNls String text, final StringBuilder buffer) {
    text = text.replaceAll("<", "&lt;");
    text = text.replaceAll(">", "&gt;");

    buffer.append(text);
  }

  private static void generateLinkValue(PsiInlineDocTag tag, StringBuilder buffer, boolean plainLink) {
    PsiElement[] tagElements = tag.getDataElements();
    String text = createLinkText(tagElements);
    if (!text.isEmpty()) {
      int index = JavaDocUtil.extractReference(text);
      String refText = text.substring(0, index).trim();
      String label = text.substring(index).trim();
      if (label.isEmpty()) {
        label = null;
      }
      generateLink(buffer, refText, label, tagElements[0], plainLink);
    }
  }

  private void generateValueValue(final PsiInlineDocTag tag, final StringBuilder buffer, final PsiElement element) {
    String text = createLinkText(tag.getDataElements());
    PsiField valueField = null;
    if (text.isEmpty()) {
      if (myElement instanceof PsiField) valueField = (PsiField) myElement;
    }
    else {
      PsiElement target = JavaDocUtil.findReferenceTarget(PsiManager.getInstance(myProject), text, myElement);
      if (target instanceof PsiField) {
        valueField = (PsiField) target;
      }
    }

    Object value = null;
    if (valueField != null) {
      PsiExpression initializer = valueField.getInitializer();
      value = JavaConstantExpressionEvaluator.computeConstantExpression(initializer, false);
    }

    if (value != null) {
      buffer.append(value);
    }
    else {
      buffer.append(element.getText());
    }
  }

  private static String createLinkText(final PsiElement[] tagElements) {
    int predictOffset = tagElements.length > 0
                        ? tagElements[0].getTextOffset() + tagElements[0].getText().length()
                        : 0;
    StringBuilder buffer1 = new StringBuilder();
    for (int j = 0; j < tagElements.length; j++) {
      PsiElement tagElement = tagElements[j];

      if (tagElement.getTextOffset() > predictOffset) buffer1.append(" ");
      predictOffset = tagElement.getTextOffset() + tagElement.getText().length();

      buffer1.append(tagElement.getText());

      if (j < tagElements.length - 1) {
        buffer1.append(" ");
      }
    }
    return buffer1.toString().trim();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateDeprecatedSection(StringBuilder buffer, PsiDocComment comment) {
    PsiDocTag tag = comment.findTagByName("deprecated");
    if (tag != null) {
      buffer.append("<DD><DL>");
      buffer.append("<B>").append(CodeInsightBundle.message("javadoc.deprecated")).append("</B>&nbsp;");
      buffer.append("<I>");
      generateValue(buffer, tag.getDataElements(), ourEmptyElementsProvider);
      buffer.append("</I>");
      buffer.append("</DL></DD>");
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateSinceSection(StringBuilder buffer, PsiDocComment comment) {
    PsiDocTag tag = comment.findTagByName("since");
    if (tag != null) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>").append(CodeInsightBundle.message("javadoc.since")).append("</b>");
      buffer.append("<DD>");
      generateValue(buffer, tag.getDataElements(), ourEmptyElementsProvider);
      buffer.append("</DD></DL></DD>");
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void generateSeeAlsoSection(StringBuilder buffer, PsiDocComment comment) {
    PsiDocTag[] tags = comment.findTagsByName("see");
    if (tags.length > 0) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>").append(CodeInsightBundle.message("javadoc.see.also")).append("</b>");
      buffer.append("<DD>");
      for (int i = 0; i < tags.length; i++) {
        PsiDocTag tag = tags[i];
        PsiElement[] elements = tag.getDataElements();
        if (elements.length > 0) {
          String text = createLinkText(elements);

          if (text.startsWith("<")) {
            buffer.append(text);
          }
          else if (text.startsWith("\"")) {
            appendPlainText(text, buffer);
          }
          else {
            int index = JavaDocUtil.extractReference(text);
            String refText = text.substring(0, index).trim();
            String label = text.substring(index).trim();
            if (label.isEmpty()) {
              label = null;
            }
            generateLink(buffer, refText, label, comment, false);
          }
        }
        if (i < tags.length - 1) {
          buffer.append(",\n");
        }
      }
      buffer.append("</DD></DL></DD>");
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateParametersSection(StringBuilder buffer, final PsiMethod method, final PsiDocComment comment) {
    PsiParameter[] params = method.getParameterList().getParameters();
    PsiDocTag[] localTags = comment != null ? comment.findTagsByName("param") : PsiDocTag.EMPTY_ARRAY;

    LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>> collectedTags =
      new LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>>();

    for (PsiParameter param : params) {
      final String paramName = param.getName();
      Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> parmTag = findDocTag(localTags, paramName, method);

      if (parmTag != null) {
        collectedTags.addLast(parmTag);
      }
    }

    if (!collectedTags.isEmpty()) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>").append(CodeInsightBundle.message("javadoc.parameters")).append("</b>");
      for (Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag : collectedTags) {
        PsiElement[] elements = tag.first.getDataElements();
        if (elements.length == 0) continue;
        generateOneParameter(elements, buffer, tag);
      }
      buffer.append("</DD></DL></DD>");
    }
  }

  private void generateTypeParametersSection(final StringBuilder buffer, final PsiMethod method) {
    final PsiDocComment docComment = method.getDocComment();
    if (docComment == null) return;
    final PsiDocTag[] localTags = docComment.findTagsByName("param");
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    final LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>> collectedTags = new LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>>();
    for (PsiTypeParameter typeParameter : typeParameters) {
      final String paramName = "<" + typeParameter.getName() + ">";
      Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> parmTag = findDocTag(localTags, paramName, method);

      if (parmTag != null) {
        collectedTags.addLast(parmTag);
      }
    }
    generateTypeParametersSection(buffer, collectedTags);
  }

  private void generateTypeParametersSection(final StringBuilder buffer, final LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>> collectedTags) {
    if (!collectedTags.isEmpty()) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>").append(CodeInsightBundle.message("javadoc.type.parameters")).append("</b>");
      for (Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag : collectedTags) {
        PsiElement[] elements = tag.first.getDataElements();
        if (elements.length == 0) continue;
        generateOneParameter(elements, buffer, tag);
      }
      buffer.append("</DD></DL></DD>");
    }
  }

  @Nullable private Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> findDocTag(final PsiDocTag[] localTags,
                                                                                         final String paramName,
                                                                                         final PsiMethod method) {
    Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> parmTag = null;
    for (PsiDocTag localTag : localTags) {
      PsiDocTagValue value = localTag.getValueElement();

      if (value != null) {
        String tagName = value.getText();

        if (tagName != null && tagName.equals(paramName)) {
          parmTag =
          new Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>
            (localTag,
             new InheritDocProvider<PsiDocTag>() {
               @Override
               public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
                 return findInheritDocTag(method, parameterLocator(paramName));
               }

               @Override
               public PsiClass getElement() {
                 return method.getContainingClass();
               }
             });
          break;
        }
      }
    }

    if (parmTag == null) {
      parmTag = findInheritDocTag(method, parameterLocator(paramName));
    }
    return parmTag;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateOneParameter(final PsiElement[] elements,
                                    final StringBuilder buffer,
                                    final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag) {
    String text = elements[0].getText();
    buffer.append("<DD>");
    int spaceIndex = text.indexOf(' ');
    if (spaceIndex < 0) {
      spaceIndex = text.length();
    }
    String parmName = text.substring(0, spaceIndex);
    buffer.append("<code>");
    buffer.append(StringUtil.escapeXml(parmName));
    buffer.append("</code>");
    buffer.append(" - ");
    buffer.append(text.substring(spaceIndex));
    generateValue(buffer, elements, 1, mapProvider(tag.second, true));
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateReturnsSection(StringBuilder buffer, final PsiMethod method, final PsiDocComment comment) {
    PsiDocTag tag = comment == null ? null : comment.findTagByName("return");
    Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair =
      tag == null ? null : new Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>(tag,
                                                                              new InheritDocProvider<PsiDocTag>(){
                                                                                @Override
                                                                                public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
                                                                                  return findInheritDocTag(method, new ReturnTagLocator());

                                                                                }

                                                                                @Override
                                                                                public PsiClass getElement() {
                                                                                  return method.getContainingClass();
                                                                                }
                                                                              });

    if (pair == null && myElement instanceof PsiMethod) {
      pair = findInheritDocTag((PsiMethod)myElement, new ReturnTagLocator());
    }

    if (pair != null) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>").append(CodeInsightBundle.message("javadoc.returns")).append("</b>");
      buffer.append("<DD>");
      generateValue(buffer, pair.first.getDataElements(), mapProvider(pair.second, false));
      buffer.append("</DD></DL></DD>");
    }
  }

  private static PsiDocTag[] getThrowsTags(PsiDocComment comment) {
    if (comment == null) {
      return PsiDocTag.EMPTY_ARRAY;
    }

    PsiDocTag[] tags1 = comment.findTagsByName(THROWS_KEYWORD);
    PsiDocTag[] tags2 = comment.findTagsByName("exception");

    return ArrayUtil.mergeArrays(tags1, tags2);
  }

  private static boolean areWeakEqual(String one, String two) {
    return one.equals(two) || one.endsWith("." + two) || two.endsWith("." + one);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateThrowsSection(StringBuilder buffer, final PsiMethod method, final PsiDocComment comment) {
    final PsiDocTag[] localTags = getThrowsTags(comment);

    LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>> collectedTags =
      new LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>>();

    final List<PsiClassType> declaredThrows = new ArrayList<PsiClassType>(Arrays.asList(method.getThrowsList().getReferencedTypes()));

    for (int i = localTags.length - 1; i > -1; i--) {
      PsiDocTagValue valueElement = localTags[i].getValueElement();

      if (valueElement != null) {
        for (Iterator<PsiClassType> iterator = declaredThrows.iterator(); iterator.hasNext();) {
          PsiClassType classType = iterator.next();
          if (Comparing.strEqual(valueElement.getText(), classType.getClassName()) || Comparing.strEqual(valueElement.getText(), classType.getCanonicalText())) {
            iterator.remove();
            break;
          }
        }

        final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag = findInheritDocTag(method, exceptionLocator(valueElement.getText()));
        collectedTags.addFirst(new Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>(localTags[i], new InheritDocProvider<PsiDocTag>() {
          @Override
          public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
            return tag;
          }

          @Override
          public PsiClass getElement() {
            return method.getContainingClass();
          }
        }));
      }
    }


    PsiClassType[] trousers = declaredThrows.toArray(new PsiClassType[declaredThrows.size()]);

    for (PsiClassType trouser : trousers) {
      if (trouser != null) {
        String paramName = trouser.getCanonicalText();
        Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> parmTag = null;

        for (PsiDocTag localTag : localTags) {
          PsiDocTagValue value = localTag.getValueElement();

          if (value != null) {
            String tagName = value.getText();

            if (tagName != null && areWeakEqual(tagName, paramName)) {
              parmTag = new Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>(localTag, ourEmptyProvider);
              break;
            }
          }
        }

        if (parmTag == null) {
          parmTag = findInheritDocTag(method, exceptionLocator(paramName));
        }

        if (parmTag != null) {
          collectedTags.addLast(parmTag);
        }
        else {
          try {
            final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
            final PsiDocTag tag = elementFactory.createDocTagFromText("@exception " + paramName);
            collectedTags.addLast(new Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>(tag, ourEmptyProvider));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }

    if (!collectedTags.isEmpty()) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>").append(CodeInsightBundle.message("javadoc.throws")).append("</b>");
      for (Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag : collectedTags) {
        PsiElement[] elements = tag.first.getDataElements();
        if (elements.length == 0) continue;
        buffer.append("<DD>");
        String text = elements[0].getText();
        int index = JavaDocUtil.extractReference(text);
        String refText = text.substring(0, index).trim();
        generateLink(buffer, refText, null, method, false);
        String rest = text.substring(index);
        if (!rest.isEmpty() || elements.length > 1) buffer.append(" - ");
        buffer.append(rest);
        generateValue(buffer, elements, 1, mapProvider(tag.second, true));
      }
      buffer.append("</DD></DL></DD>");
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void generateSuperMethodsSection(StringBuilder buffer, PsiMethod method, boolean overrides) {
    PsiClass parentClass = method.getContainingClass();
    if (parentClass == null) return;
    if (parentClass.isInterface() && !overrides) return;
    PsiMethod[] supers = method.findSuperMethods();
    if (supers.length == 0) return;
    boolean headerGenerated = false;
    for (PsiMethod superMethod : supers) {
      boolean isAbstract = superMethod.hasModifierProperty(PsiModifier.ABSTRACT);
      if (overrides) {
        if (parentClass.isInterface() ? !isAbstract : isAbstract) continue;
      }
      else {
        if (!isAbstract) continue;
      }
      PsiClass superClass = superMethod.getContainingClass();
      if (superClass == null) continue;
      if (!headerGenerated) {
        buffer.append("<DD><DL>");
        buffer.append("<DT><b>");
        buffer.append(overrides ?
                      CodeInsightBundle.message("javadoc.method.overrides") :
                      CodeInsightBundle .message("javadoc.method.specified.by"));
        buffer.append("</b>");
        headerGenerated = true;
      }
      buffer.append("<DD>");

      StringBuilder methodBuffer = new StringBuilder();
      generateLink(methodBuffer, superMethod, superMethod.getName(), false);
      StringBuilder classBuffer = new StringBuilder();
      generateLink(classBuffer, superClass, superClass.getName(), false);
      if (superClass.isInterface()) {
        buffer.append(CodeInsightBundle.message("javadoc.method.in.interface", methodBuffer.toString(), classBuffer.toString()));
      }
      else {
        buffer.append(CodeInsightBundle.message("javadoc.method.in.class", methodBuffer.toString(), classBuffer.toString()));
      }
    }
    if (headerGenerated) {
      buffer.append("</DD></DL></DD>");
    }
  }

  private static void generateLink(StringBuilder buffer, PsiElement element, String label, boolean plainLink) {
    String refText = JavaDocUtil.getReferenceText(element.getProject(), element);
    if (refText != null) {
      DocumentationManagerUtil.createHyperlink(buffer, element, refText, label, plainLink);
      //return generateLink(buffer, refText, label, context, false);
    }
  }

  /**
   * @return Length of the generated label.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  private static int generateLink(StringBuilder buffer, String refText, String label, @NotNull PsiElement context, boolean plainLink) {
    if (label == null) {
      final PsiManager manager = context.getManager();
      label = JavaDocUtil.getLabelText(manager.getProject(), manager, refText, context);
    }

    LOG.assertTrue(refText != null, "refText appears to be null.");

    final PsiElement target = JavaDocUtil.findReferenceTarget(context.getManager(), refText, context);
    boolean isBrokenLink = target == null;
    if (isBrokenLink) {
      buffer.append("<font color=red>");
      buffer.append(label);
      buffer.append("</font>");
      return label.length();
    }


    generateLink(buffer, target, label, plainLink);
    return label.length();
  }

  /**
   * @return Length of the generated label.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static int generateType(StringBuilder buffer, PsiType type, PsiElement context) {
    return generateType(buffer, type, context, true);
  }

  /**
   * @return Length of the generated label.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static int generateType(StringBuilder buffer, PsiType type, PsiElement context, boolean generateLink) {
    if (type instanceof PsiPrimitiveType) {
      String text = StringUtil.escapeXml(type.getCanonicalText());
      buffer.append(text);
      return text.length();
    }

    if (type instanceof PsiArrayType) {
      int rest = generateType(buffer, ((PsiArrayType)type).getComponentType(), context, generateLink);
      if (type instanceof PsiEllipsisType) {
        buffer.append("...");
        return rest + 3;
      }
      else {
        buffer.append("[]");
        return rest + 2;
      }
    }

    if (type instanceof PsiCapturedWildcardType) {
      type = ((PsiCapturedWildcardType)type).getWildcard();
    }

    if (type instanceof PsiWildcardType){
      PsiWildcardType wt = (PsiWildcardType)type;

      buffer.append("?");

      PsiType bound = wt.getBound();

      if (bound != null){
        final String keyword = wt.isExtends() ? " extends " : " super ";
        buffer.append(keyword);
        return generateType(buffer, bound, context, generateLink) + 1 + keyword.length();
      }

      return 1;
    }

    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
      PsiClass psiClass = result.getElement();
      PsiSubstitutor psiSubst = result.getSubstitutor();

      if (psiClass == null) {
        String text = "<font color=red>" + StringUtil.escapeXml(type.getCanonicalText()) + "</font>";
        buffer.append(text);
        return text.length();
      }

      String qName = psiClass.getQualifiedName();

      if (qName == null || psiClass instanceof PsiTypeParameter) {
        String text = StringUtil.escapeXml(type.getCanonicalText());
        buffer.append(text);
        return text.length();
      }

      int length;
      if (generateLink) {
        length = generateLink(buffer, qName, null, context, false);
      }
      else {
        buffer.append(qName);
        length = buffer.length();
      }

      if (psiClass.hasTypeParameters()) {
        StringBuilder subst = new StringBuilder();

        PsiTypeParameter[] params = psiClass.getTypeParameters();

        subst.append("&lt;");
        boolean goodSubst = true;
        for (int i = 0; i < params.length; i++) {
          PsiType t = psiSubst.substitute(params[i]);

          if (t == null) {
            goodSubst = false;
            break;
          }

          length += generateType(subst, t, context, generateLink);

          if (i < params.length - 1) {
            subst.append(", ");
          }
        }

        subst.append("&gt;");
        if (goodSubst) {
          String text = subst.toString();

          buffer.append(text);
        }
      }

      return length;
    }

    if (type instanceof PsiDisjunctionType || type instanceof PsiIntersectionType) {
      if (!generateLink) {
        final String text = StringUtil.escapeXml(type.getCanonicalText());
        buffer.append(text);
        return text.length();
      }
      else {
        final String separator = type instanceof PsiDisjunctionType ? " | " : " & ";
        final List<PsiType> componentTypes;
        if (type instanceof PsiIntersectionType) {
          componentTypes = Arrays.asList(((PsiIntersectionType)type).getConjuncts());
        }
        else {
          componentTypes = ((PsiDisjunctionType)type).getDisjunctions();
        }
        int length = 0;
        for (PsiType psiType : componentTypes) {
          if (length > 0) {
            buffer.append(separator);
            length += 3;
          }
          length += generateType(buffer, psiType, context, generateLink);
        }
        return length;
      }
    }

    return 0;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String generateTypeParameters(PsiTypeParameterListOwner owner) {
    if (owner.hasTypeParameters()) {
      PsiTypeParameter[] parms = owner.getTypeParameters();

      StringBuilder buffer = new StringBuilder();

      buffer.append("&lt;");

      for (int i = 0; i < parms.length; i++) {
        PsiTypeParameter p = parms[i];

        buffer.append(p.getName());

        PsiClassType[] refs = JavaDocUtil.getExtendsList(p);

        if (refs.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < refs.length; j++) {
            generateType(buffer, refs[j], owner);

            if (j < refs.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < parms.length - 1) {
          buffer.append(", ");
        }
      }

      buffer.append("&gt;");

      return buffer.toString();
    }

    return "";
  }

  private <T> Pair<T, InheritDocProvider<T>> searchDocTagInOverridenMethod(PsiMethod method,
                                                                           final PsiClass aSuper,
                                                                           final DocTagLocator<T> loc) {
    if (aSuper != null) {

      final PsiMethod overriden =  findMethodInSuperClass(method, aSuper);

      if (overriden != null) {
        T tag = loc.find(getDocComment(overriden));

        if (tag != null) {
          return new Pair<T, InheritDocProvider<T>>
            (tag,
             new InheritDocProvider<T>() {
               @Override
               public Pair<T, InheritDocProvider<T>> getInheritDoc() {
                 return findInheritDocTag(overriden, loc);
               }

               @Override
               public PsiClass getElement() {
                 return aSuper;
               }
             });
        }
      }
    }

    return null;
  }

  @Nullable
  private static PsiMethod findMethodInSuperClass(PsiMethod method, PsiClass aSuper) {
    for (PsiMethod superMethod : method.findDeepestSuperMethods()) {
      PsiMethod overriden = aSuper.findMethodBySignature(superMethod, false);
      if (overriden != null) {
        return overriden;
      }
    }
    return null;
  }

  @Nullable private <T> Pair<T, InheritDocProvider<T>> searchDocTagInSupers(PsiClassType[] supers,
                                                                  PsiMethod method,
                                                                  DocTagLocator<T> loc) {
    for (PsiClassType superType : supers) {
      PsiClass aSuper = superType.resolve();

      if (aSuper != null) {
        Pair<T, InheritDocProvider<T>> tag = searchDocTagInOverridenMethod(method, aSuper, loc);

        if (tag != null) return tag;
      }
    }

    for (PsiClassType superType : supers) {
      PsiClass aSuper = superType.resolve();

      if (aSuper != null) {
        Pair<T, InheritDocProvider<T>> tag = findInheritDocTagInClass(method, aSuper, loc);

        if (tag != null) {
          return tag;
        }
      }
    }

    return null;
  }

  private <T> Pair<T, InheritDocProvider<T>> findInheritDocTagInClass(PsiMethod aMethod,
                                                                      PsiClass aClass,
                                                                      DocTagLocator<T> loc) {
    if (aClass == null) {
      return null;
    }

    PsiClassType[] implementsTypes = aClass.getImplementsListTypes();
    Pair<T, InheritDocProvider<T>> tag = searchDocTagInSupers(implementsTypes, aMethod, loc);

    if (tag != null) {
      return tag;
    }

    PsiClassType[] extendsTypes = aClass.getExtendsListTypes();
    return searchDocTagInSupers(extendsTypes, aMethod, loc);
  }

  @Nullable private <T> Pair<T, InheritDocProvider<T>> findInheritDocTag(PsiMethod method, DocTagLocator<T> loc) {
    PsiClass aClass = method.getContainingClass();

    if (aClass == null) return null;

    return findInheritDocTagInClass(method, aClass, loc);
  }

  private static class ReturnTagLocator implements DocTagLocator<PsiDocTag> {
    @Override
    public PsiDocTag find(PsiDocComment comment) {
      if (comment == null) {
        return null;
      }

      return comment.findTagByName("return");
    }
  }
  
  private static class MyVisitor extends JavaElementVisitor {

    @NotNull private final StringBuilder myBuffer;

    MyVisitor(@NotNull StringBuilder buffer) {
      myBuffer = buffer;
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      myBuffer.append("new ");
      PsiType type = expression.getType();
      if (type != null) {
        generateType(myBuffer, type, expression);
      }
      myBuffer.append("(");
      expression.acceptChildren(this);
      myBuffer.append(")");
    }

    @Override
    public void visitExpressionList(PsiExpressionList list) {
      String separator = ", ";
      PsiExpression[] expressions = list.getExpressions();
      for (PsiExpression expression : expressions) {
        expression.accept(this);
        myBuffer.append(separator);
      }
      if (expressions.length > 0) {
        myBuffer.setLength(myBuffer.length() - separator.length());
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      myBuffer.append(expression.getMethodExpression().getText()).append("(");
      expression.getArgumentList().acceptChildren(this);
      myBuffer.append(")");
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      myBuffer.append(StringUtil.escapeXml(expression.getText()));
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      myBuffer.append(StringUtil.escapeXml(expression.getText()));
    }
  }
}
