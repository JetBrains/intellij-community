package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.ConstantExpressionEvaluator;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class JavaDocInfoGenerator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.javadoc.JavaDocInfoGenerator");

  private static final @NonNls Pattern ourNotDot = Pattern.compile("[^.]");
  private static final @NonNls Pattern ourWhitespaces = Pattern.compile("[ \\n\\r\\t]+");
  private static final @NonNls Matcher ourNotDotMatcher = ourNotDot.matcher("");
  private static final @NonNls Matcher ourWhitespacesMatcher = ourWhitespaces.matcher("");

  private Project myProject;
  private PsiElement myElement;
  private JavaDocManager.DocumentationProvider myProvider;
  private static final @NonNls String THROWS_KEYWORD = "throws";
  private static final @NonNls String BR_TAG = "<br>";
  private static final @NonNls String LINK_TAG = "link";
  private static final @NonNls String LITERAL_TAG = "literal";
  private static final @NonNls String CODE_TAG = "code";
  private static final @NonNls String LINKPLAIN_TAG = "linkplain";
  private static final @NonNls String INHERITDOC_TAG = "inheritDoc";
  private static final @NonNls String DOCROOT_TAG = "docRoot";
  private static final @NonNls String VALUE_TAG = "value";

  interface InheritDocProvider <T> {
    Pair<T, InheritDocProvider<T>> getInheritDoc();

    PsiClass getElement();
  }

  private static final InheritDocProvider<PsiDocTag> ourEmptyProvider = new InheritDocProvider<PsiDocTag>() {
    public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
      return null;
    }

    public PsiClass getElement() {
      return null;
    }
  };

  private static final InheritDocProvider<PsiElement[]> ourEmptyElementsProvider = mapProvider(ourEmptyProvider, false);

  private static InheritDocProvider<PsiElement[]> mapProvider(final InheritDocProvider<PsiDocTag> i,
                                                              final boolean dropFirst) {
    return new InheritDocProvider<PsiElement[]>() {
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

  public JavaDocInfoGenerator(Project project, PsiElement element,
                              JavaDocManager.DocumentationProvider _provider) {
    myProject = project;
    myElement = element;
    myProvider = _provider;
  }

  public String generateDocInfo() {
    StringBuffer buffer = new StringBuffer();
    if (myElement instanceof PsiClass) {
      generateClassJavaDoc(buffer, (PsiClass)myElement);
    }
    else if (myElement instanceof PsiMethod) {
      generateMethodJavaDoc(buffer, (PsiMethod)myElement);
    } else if (myElement instanceof PsiParameter) {
      generateMethodParameterJavaDoc(buffer, (PsiParameter)myElement);
    }
    else if (myElement instanceof PsiField) {
      generateFieldJavaDoc(buffer, (PsiField)myElement);
    }
    else if (myElement instanceof PsiVariable) {
      generateVariableJavaDoc(buffer, (PsiVariable)myElement);
    }
    else if (myElement instanceof PsiFile) {
      generateFileJavaDoc(buffer, (PsiFile)myElement); //used for Ctrl-Click
    }
    else if (myElement instanceof PsiPackage) {
      generatePackageJavaDoc(buffer, (PsiPackage) myElement);
    }
    else {
      if (myProvider!=null) {
        return myProvider.generateDoc(myElement,myElement.getUserData(JavaDocManager.ORIGINAL_ELEMENT_KEY));
      }
      return null;
    }
    String text = buffer.toString();
    if (text.length() == 0) {
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Generated JavaDoc:");
      LOG.debug(text);
    }

    text = StringUtil.replace(text, "/>", ">");

    return text;
  }

  private void generateClassJavaDoc(@NonNls StringBuffer buffer, PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) return;
    PsiManager manager = aClass.getManager();
    generatePrologue(buffer);

    PsiFile file = aClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile)file).getPackageName();
      if (packageName.length() > 0) {
        buffer.append("<font size=\"-1\"><b>");
        buffer.append(packageName);
        buffer.append("</b></font>");
        //buffer.append("<br>");
      }
    }

    buffer.append("<PRE>");
    generateAnnotations(buffer, aClass);
    String modifiers = PsiFormatUtil.formatModifiers(aClass, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);
    if (modifiers.length() > 0) {
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

    if (refs.length > 0 || !aClass.isInterface() && (qName == null || !qName.equals("java.lang.Object"))) {
      buffer.append("extends ");
      if (refs.length == 0) {
        generateLink(buffer, "java.lang.Object", null, aClass, false);
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
      generateDescription(buffer, comment);
      generateDeprecatedSection(buffer, comment);
      generateSinceSection(buffer, comment);
      generateSeeAlsoSection(buffer, comment);
    }

    generateEpilogue(buffer);
  }

  private static PsiDocComment getDocComment(final PsiDocCommentOwner docOwner) {
    return ((PsiDocCommentOwner)docOwner.getNavigationElement()).getDocComment();
  }

  private void generateFieldJavaDoc(@NonNls StringBuffer buffer, PsiField field) {
    generatePrologue(buffer);

    PsiClass parentClass = field.getContainingClass();
    if (parentClass != null) {
      String qName = parentClass.getQualifiedName();
      if (qName != null) {
        buffer.append("<font size=\"-1\"><b>");
        //buffer.append(qName);
        generateLink(buffer, qName, qName, field, false);
        buffer.append("</b></font>");
        //buffer.append("<br>");
      }
    }

    buffer.append("<PRE>");
    generateAnnotations(buffer, field);
    String modifiers = PsiFormatUtil.formatModifiers(field, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);
    if (modifiers.length() > 0) {
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

    PsiDocComment comment = getDocComment(field);
    if (comment != null) {
      generateDescription(buffer, comment);
      generateDeprecatedSection(buffer, comment);
      generateSinceSection(buffer, comment);
      generateSeeAlsoSection(buffer, comment);
    }

    generateEpilogue(buffer);
  }

  // not a javadoc in fact..
  private void generateVariableJavaDoc(@NonNls StringBuffer buffer, PsiVariable variable) {
    generatePrologue(buffer);

    buffer.append("<PRE>");
    String modifiers = PsiFormatUtil.formatModifiers(variable, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);
    if (modifiers.length() > 0) {
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

    generateEpilogue(buffer);
  }

  // not a javadoc in fact..
  private static void generateFileJavaDoc(StringBuffer buffer, PsiFile file) {
    generatePrologue(buffer);
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      buffer.append(virtualFile.getPresentableUrl());
    }
    generateEpilogue(buffer);
  }

  private void generatePackageJavaDoc(final StringBuffer buffer, final PsiPackage psiPackage) {
    for(PsiDirectory directory: psiPackage.getDirectories()) {
      PsiFile packageHtmlFile = directory.findFile("package.html");
      if (packageHtmlFile != null) {
        generatePackageHtmlJavaDoc(buffer, packageHtmlFile);
        break;
      }
    }
  }

  private void generatePackageHtmlJavaDoc(final StringBuffer buffer, final PsiFile packageHtmlFile) {
    String htmlText;
    XmlFile packageXmlFile = (XmlFile) packageHtmlFile;
    final XmlTag rootTag = packageXmlFile.getDocument().getRootTag();
    if (rootTag != null) {
      final XmlTag subTag = rootTag.findFirstSubTag("body");
      if (subTag != null) {
        htmlText = subTag.getValue().getText();
      }
      else {
        htmlText = packageHtmlFile.getText();
      }
    }
    else {
      htmlText = packageHtmlFile.getText();
    }
    htmlText = StringUtil.replace(htmlText, "*/", "&#42;&#47;");

    final String fileText = "/** " + htmlText + " */";
    final PsiElementFactory elementFactory = packageHtmlFile.getManager().getElementFactory();
    final PsiDocComment docComment;
    try {
      docComment = elementFactory.createDocCommentFromText(fileText, null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    generatePrologue(buffer);

    generateDescription(buffer, docComment);
    generateSinceSection(buffer, docComment);
    generateSeeAlsoSection(buffer, docComment);

    generateEpilogue(buffer);
  }

  private static void appendInitializer(StringBuffer buffer, PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      String text = initializer.getText();
      text = text.trim();
      int index1 = text.indexOf('\n');
      if (index1 < 0) index1 = text.length();
      int index2 = text.indexOf('\r');
      if (index2 < 0) index2 = text.length();
      int index = Math.min(index1, index2);
      boolean trunc = index < text.length();
      text = text.substring(0, index);
      buffer.append(" = ");
      text = StringUtil.replace(text, "<", "&lt;");
      text = StringUtil.replace(text, ">", "&gt;");
      buffer.append(text);
      if (trunc) {
        buffer.append("...");
      }
    }
  }

  private void generateAnnotations (@NonNls StringBuffer buffer, PsiDocCommentOwner owner) {
    final PsiModifierList ownerModifierList = owner.getModifierList();
    if (ownerModifierList == null) return;
    final PsiAnnotation[] annotations = ownerModifierList.getAnnotations();
    PsiManager manager = owner.getManager();
    for (PsiAnnotation annotation : annotations) {
      final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
      if (nameReferenceElement == null) continue;
      final PsiElement resolved = nameReferenceElement.resolve();
      if (resolved instanceof PsiClass) {
        final PsiClass annotationType = (PsiClass)resolved;
        final PsiModifierList modifierList = annotationType.getModifierList();
        if (modifierList.findAnnotation("java.lang.annotation.Documented") != null) {
          final PsiClassType type = manager.getElementFactory().createType(annotationType, PsiSubstitutor.EMPTY);
          buffer.append("@");
          generateType(buffer, type, owner);
          final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
          if (attributes.length > 0) {
            boolean first = true;
            buffer.append("(");
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
          buffer.append("\n");
        }
      }
    }
  }

  private void generateMethodParameterJavaDoc(@NonNls StringBuffer buffer, PsiParameter parameter) {
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
  }

  private void generateMethodJavaDoc(@NonNls StringBuffer buffer, PsiMethod method) {
    generatePrologue(buffer);

    PsiClass parentClass = method.getContainingClass();
    if (parentClass != null) {
      String qName = parentClass.getQualifiedName();
      if (qName != null) {
        buffer.append("<font size=\"-1\"><b>");
        generateLink(buffer, qName, qName, method, false);
        //buffer.append(qName);
        buffer.append("</b></font>");
        //buffer.append("<br>");
      }
    }

    buffer.append("<PRE>");
    int indent = 0;
    generateAnnotations(buffer, method);
    String modifiers = PsiFormatUtil.formatModifiers(method, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);
    if (modifiers.length() > 0) {
      buffer.append(modifiers);
      buffer.append("&nbsp;");
      indent += modifiers.length() + 1;
    }

    final String typeParamsString = generateTypeParameters(method);
    indent += typeParamsString.length();
    if (typeParamsString.length() > 0) {
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
      generateType(buffer, parm.getType(), method);
      buffer.append("&nbsp;");
      if (parm.getName() != null) {
        buffer.append(parm.getName());
      }
      if (i < parms.length - 1) {
        buffer.append(",\n");
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
    generateReturnsSection(buffer, method, comment);
    generateThrowsSection(buffer, method, comment);

    if (comment != null) {
      generateSinceSection(buffer, comment);
      generateSeeAlsoSection(buffer, comment);
    }

    generateEpilogue(buffer);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private PsiDocComment getMethodDocComment(final PsiMethod method) {
    final PsiClass parentClass = method.getContainingClass();
    if (parentClass != null && parentClass.isEnum()) {
      if (method.getName().equals("values") && method.getParameterList().getParameters().length == 0) {
        return loadSyntheticDocComment(method, "/javadoc/EnumValues.java.template");
      }
      if (method.getName().equals("valueOf") && method.getParameterList().getParameters().length == 1) {
        return loadSyntheticDocComment(method, "/javadoc/EnumValueOf.java.template");
      }
    }
    return getDocComment(method);
  }

  private PsiDocComment loadSyntheticDocComment(final PsiMethod method, final String resourceName) {
    final InputStream commentStream = JavaDocInfoGenerator.class.getResourceAsStream(resourceName);
    if (commentStream == null) {
      return null;
    }

    final StringBuffer buffer;
    try {
      buffer = new StringBuffer();
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
    final PsiElementFactory elementFactory = PsiManager.getInstance(myProject).getElementFactory();
    try {
      return elementFactory.createDocCommentFromText(s, null);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void generatePrologue(StringBuffer buffer) {
    buffer.append("<html><body>");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void generateEpilogue(StringBuffer buffer) {
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

  private void generateDescription(StringBuffer buffer, PsiDocComment comment) {
    PsiElement[] elements = comment.getDescriptionElements();
    generateValue(buffer, elements, ourEmptyElementsProvider);
  }

  private static boolean isEmptyDescription(PsiDocComment comment) {
    PsiElement[] descriptionElements = comment.getDescriptionElements();

    for (PsiElement description : descriptionElements) {
      String text = description.getText();

      if (text != null) {
        if (ourWhitespacesMatcher.reset(text).replaceAll("").length() != 0) {
          return false;
        }
      }
    }

    return true;
  }

  private void generateMethodDescription(@NonNls StringBuffer buffer, final PsiMethod method, final PsiDocComment comment) {
    final DocTagLocator<PsiElement[]> descriptionLocator = new DocTagLocator<PsiElement[]>() {
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
             public Pair<PsiElement[], InheritDocProvider<PsiElement[]>> getInheritDoc() {
               return findInheritDocTag(method, descriptionLocator);
             }

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
        generateLink(buffer, extendee, JavaDocUtil.getShortestClassName(extendee, method));
        buffer.append(BR_TAG);
        generateValue(buffer, elements, pair.second);
        buffer.append("</DD></DL></DD>");
      }
    }
  }

  private void generateValue(StringBuffer buffer, PsiElement[] elements, InheritDocProvider<PsiElement[]> provider) {
    generateValue(buffer, elements, 0, provider);
  }

  private String getDocRoot() {
    PsiClass aClass = null;

    if (myElement instanceof PsiClass) {
      aClass = (PsiClass)myElement;
    }
    else if (myElement instanceof PsiMember) {
      aClass = ((PsiMember)myElement).getContainingClass();
    }
    else {
      LOG.error("Class or member expected but found " + myElement.getClass().getName());
    }

    String qName = aClass.getQualifiedName();

    if (qName == null) {
      return "";
    }

    return "../" + ourNotDotMatcher.reset(qName).replaceAll("").replaceAll(".", "../");
  }

  private void generateValue(StringBuffer buffer,
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
        if (tagName == null) {
          buffer.append(element.getText());
        }
        else if (tagName.equals(LINK_TAG)) {
          generateLinkValue(tag, buffer, false);
        }
        else if (tagName.equals(LITERAL_TAG)) {
          generateLiteralValue(tag, buffer);
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
  private static void generateCodeValue(PsiInlineDocTag tag, StringBuffer buffer) {
    buffer.append("<code>");
    generateLiteralValue(tag, buffer);
    buffer.append("</code>");
  }

  private static void generateLiteralValue(PsiInlineDocTag tag, StringBuffer buffer) {
    PsiElement[] elements = tag.getDataElements();

    for (PsiElement element : elements) {
      appendPlainText(element.getText(), buffer);
    }
  }

  private static void appendPlainText(@NonNls String text, final StringBuffer buffer) {
    text = text.replaceAll("<", "&lt;");
    text = text.replaceAll(">", "&gt;");

    buffer.append(text);
  }

  private void generateLinkValue(PsiInlineDocTag tag, StringBuffer buffer, boolean plainLink) {
    PsiElement[] tagElements = tag.getDataElements();
    String text = createLinkText(tagElements);
    if (text.length() > 0) {
      int index = JavaDocUtil.extractReference(text);
      String refText = text.substring(0, index).trim();
      String label = text.substring(index).trim();
      if (label.length() == 0) {
        label = null;
      }
      generateLink(buffer, refText, label, tagElements[0], plainLink);
    }
  }

  private void generateValueValue(final PsiInlineDocTag tag, final StringBuffer buffer, final PsiElement element) {
    String text = createLinkText(tag.getDataElements());
    PsiField valueField = null;
    if (text.length() == 0) {
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
      value = ConstantExpressionEvaluator.computeConstantExpression(initializer, null, false);
    }

    if (value != null) {
      buffer.append(value.toString());
    }
    else {
      buffer.append(element.getText());
    }
  }

  private static String createLinkText(final PsiElement[] tagElements) {
    int predictOffset = tagElements.length > 0
                        ? tagElements[0].getTextOffset() + tagElements[0].getText().length()
                        : 0;
    StringBuffer buffer1 = new StringBuffer();
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
  private void generateDeprecatedSection(StringBuffer buffer, PsiDocComment comment) {
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
  private void generateSinceSection(StringBuffer buffer, PsiDocComment comment) {
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
  private void generateSeeAlsoSection(StringBuffer buffer, PsiDocComment comment) {
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
            if (label.length() == 0) {
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
  private void generateParametersSection(StringBuffer buffer, final PsiMethod method, final PsiDocComment comment) {
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

    if (collectedTags.size() > 0) {
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
               public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
                 return findInheritDocTag(method, parameterLocator(paramName));
               }

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
                                    final StringBuffer buffer,
                                    final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag) {
    String text = elements[0].getText();
    buffer.append("<DD>");
    int spaceIndex = text.indexOf(' ');
    if (spaceIndex < 0) {
      spaceIndex = text.length();
    }
    String parmName = text.substring(0, spaceIndex);
    buffer.append("<code>");
    buffer.append(parmName);
    buffer.append("</code>");
    buffer.append(" - ");
    buffer.append(text.substring(spaceIndex));
    generateValue(buffer, elements, 1, mapProvider(tag.second, true));
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateReturnsSection(StringBuffer buffer, final PsiMethod method, final PsiDocComment comment) {
    PsiDocTag tag = comment == null ? null : comment.findTagByName("return");
    Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair =
      tag == null ? null : new Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>(tag,
                                                                              new InheritDocProvider<PsiDocTag>(){
                                                                                public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
                                                                                  return findInheritDocTag(method, new ReturnTagLocator());

                                                                                }

                                                                                public PsiClass getElement() {
                                                                                  return method.getContainingClass();
                                                                                }
                                                                              });

    if (pair == null && myElement instanceof PsiMethod) {
      pair = findInheritDocTag(((PsiMethod)myElement), new ReturnTagLocator());
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

    return ArrayUtil.mergeArrays(tags1, tags2, PsiDocTag.class);
  }

  private static boolean areWeakEqual(String one, String two) {
    return one.equals(two) || one.endsWith("." + two) || two.endsWith("." + one);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateThrowsSection(StringBuffer buffer, PsiMethod method, final PsiDocComment comment) {
    PsiDocTag[] localTags = getThrowsTags(comment);

    LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>> collectedTags =
      new LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>>();

    LinkedList<PsiClassType> holder = new LinkedList<PsiClassType>(Arrays.asList(method.getThrowsList().getReferencedTypes()));

    for (int i = localTags.length - 1; i > -1; i--) {
      try {
        PsiDocTagValue valueElement = localTags[i].getValueElement();

        if (valueElement != null) {
          PsiClassType t = (PsiClassType)method.getManager().getElementFactory().createTypeFromText(valueElement.getText(), method);

          if (!holder.contains(t)) {
            holder.addFirst(t);
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error("Incorrect operation exception.");
      }
    }

    PsiClassType[] trousers = holder.toArray(new PsiClassType[holder.size()]);

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
            final PsiDocTag tag = method.getManager().getElementFactory().createDocCommentFromText("/** @exception " + paramName + " */",
                                                                                                   method.getContainingFile()).getTags()[0];

            collectedTags.addLast(new Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>(tag, ourEmptyProvider));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }

    if (collectedTags.size() > 0) {
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
        if (rest.length() > 0 || elements.length > 1) buffer.append(" - ");
        buffer.append(rest);
        generateValue(buffer, elements, 1, mapProvider(tag.second, true));
      }
      buffer.append("</DD></DL></DD>");
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateSuperMethodsSection(StringBuffer buffer, PsiMethod method, boolean overrides) {
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

      StringBuffer methodBuffer = new StringBuffer();
      generateLink(methodBuffer, superMethod, superMethod.getName());
      StringBuffer classBuffer = new StringBuffer();
      generateLink(classBuffer, superClass, superClass.getName());
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

  private void generateLink(StringBuffer buffer, PsiElement element, String label) {
    String refText = JavaDocUtil.getReferenceText(myProject, element);
    if (refText != null) {
      JavaDocUtil.createHyperlink(buffer, refText,label,false);
      //return generateLink(buffer, refText, label, context, false);
    }
  }

  /**
   * @return Length of the generated label.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  private int generateLink(StringBuffer buffer, String refText, String label, PsiElement context, boolean plainLink) {
    if (label == null) {
      PsiManager manager = PsiManager.getInstance(myProject);
      label = JavaDocUtil.getLabelText(myProject, manager, refText, context);
    }

    LOG.assertTrue(refText != null, "refText appears to be null.");

    boolean isBrokenLink = JavaDocUtil.findReferenceTarget(context.getManager(), refText, context) == null;
    if (isBrokenLink) {
      buffer.append("<font color=red>");
      buffer.append(label);
      buffer.append("</font>");
      return label.length();
    }


    JavaDocUtil.createHyperlink(buffer, refText,label,plainLink);
    return label.length();
  }

  /**
   * @return Length of the generated label.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  private int generateType(StringBuffer buffer, PsiType type, PsiElement context) {
    if (type instanceof PsiPrimitiveType) {
      String text = type.getCanonicalText();
      buffer.append(text);
      return text.length();
    }

    if (type instanceof PsiArrayType) {
      int rest = generateType(buffer, ((PsiArrayType)type).getComponentType(), context);
      if ((type instanceof PsiEllipsisType)) {
        buffer.append("...");
        return rest + 3;
      }
      else {
        buffer.append("[]");
        return rest + 2;
      }
    }

    if (type instanceof PsiWildcardType){
      PsiWildcardType wt = ((PsiWildcardType)type);

      buffer.append("?");

      PsiType bound = wt.getBound();

      if (bound != null){
        final String keyword = wt.isExtends() ? " extends " : " super ";
        buffer.append(keyword);
        return generateType(buffer, bound, context) + 1 + keyword.length();
      }

      return 1;
    }

    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
      PsiClass psiClass = result.getElement();
      PsiSubstitutor psiSubst = result.getSubstitutor();

      if (psiClass == null) {
        String text = "<font color=red>" + type.getCanonicalText() + "</font>";
        buffer.append(text);
        return text.length();
      }

      String qName = psiClass.getQualifiedName();

      if (qName == null || psiClass instanceof PsiTypeParameter) {
        String text = type.getCanonicalText();
        buffer.append(text);
        return text.length();
      }

      int length = generateLink(buffer, qName, null, context, false);

      if (psiClass.hasTypeParameters()) {
        StringBuffer subst = new StringBuffer();
        boolean goodSubst = true;

        PsiTypeParameter[] params = psiClass.getTypeParameters();

        subst.append("&lt;");
        for (int i = 0; i < params.length; i++) {
          PsiType t = psiSubst.substitute(params[i]);

          if (t == null) {
            goodSubst = false;
            break;
          }

          generateType(subst, t, context);

          if (i < params.length - 1) {
            subst.append(", ");
          }
        }

        if (goodSubst) {
          subst.append("&gt;");
          String text = subst.toString();

          buffer.append(text);
          length += text.length();
        }
      }

      return length;
    }

    return 0;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private String generateTypeParameters(PsiTypeParameterListOwner owner) {
    if (owner.hasTypeParameters()) {
      PsiTypeParameter[] parms = owner.getTypeParameters();

      StringBuffer buffer = new StringBuffer();

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
      final PsiMethod overriden = aSuper.findMethodBySignature(method, false);

      if (overriden != null) {
        T tag = loc.find(getDocComment(overriden));

        if (tag != null) {
          return new Pair<T, InheritDocProvider<T>>
            (tag,
             new InheritDocProvider<T>() {
               public Pair<T, InheritDocProvider<T>> getInheritDoc() {
                 return findInheritDocTag(overriden, loc);
               }

               public PsiClass getElement() {
                 return aSuper;
               }
             });
        }
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
    public PsiDocTag find(PsiDocComment comment) {
      if (comment == null) {
        return null;
      }

      return comment.findTagByName("return");
    }
  }
}
