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
package com.intellij.psi.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Properties;

/**
 * @author max
 */
public class PsiJavaParserFacadeImpl extends PsiParserFacadeImpl implements PsiJavaParserFacade {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiJavaParserFacadeImpl");

  private static final JavaParserUtil.ParserWrapper ANNOTATION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      DeclarationParser.parseAnnotation(builder);
    }
  };

  private static final JavaParserUtil.ParserWrapper PARAMETER = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      DeclarationParser.parseParameter(builder, true, false);
    }
  };

  private static final JavaParserUtil.ParserWrapper RESOURCE = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      DeclarationParser.parseResource(builder);
    }
  };

  private static final JavaParserUtil.ParserWrapper TYPE = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      ReferenceParser.parseType(builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.ELLIPSIS |
                                         ReferenceParser.WILDCARD | ReferenceParser.DISJUNCTIONS);
    }
  };

  public static final JavaParserUtil.ParserWrapper REFERENCE = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      ReferenceParser.parseJavaCodeReference(builder, false, true, false, false, false);
    }
  };

  public static final JavaParserUtil.ParserWrapper DIAMOND_REF = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      ReferenceParser.parseJavaCodeReference(builder, false, true, false, false, true);
    }
  };

  public static final JavaParserUtil.ParserWrapper STATIC_IMPORT_REF = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      ReferenceParser.parseImportCodeReference(builder, true);
    }
  };

  private static final JavaParserUtil.ParserWrapper TYPE_PARAMETER = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      ReferenceParser.parseTypeParameter(builder);
    }
  };

  private static final JavaParserUtil.ParserWrapper DECLARATION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      DeclarationParser.parse(builder, DeclarationParser.Context.CLASS);
    }
  };

  private static final JavaParserUtil.ParserWrapper CODE_BLOCK = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      StatementParser.parseCodeBlockDeep(builder, true);
    }
  };

  private static final JavaParserUtil.ParserWrapper STATEMENT = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      StatementParser.parseStatement(builder);
    }
  };

  private static final JavaParserUtil.ParserWrapper EXPRESSION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      ExpressionParser.parse(builder);
    }
  };

  private static final JavaParserUtil.ParserWrapper ENUM_CONSTANT = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      DeclarationParser.parseEnumConstant(builder);
    }
  };

  private static final JavaParserUtil.ParserWrapper CATCH_SECTION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      StatementParser.parseCatchBlock(builder);
    }
  };

  private static final Map<String, PsiPrimitiveType> PRIMITIVE_TYPES;
  static {
    PRIMITIVE_TYPES = new HashMap<String, PsiPrimitiveType>();
    PRIMITIVE_TYPES.put(PsiType.BYTE.getCanonicalText(), PsiType.BYTE);
    PRIMITIVE_TYPES.put(PsiType.CHAR.getCanonicalText(), PsiType.CHAR);
    PRIMITIVE_TYPES.put(PsiType.DOUBLE.getCanonicalText(), PsiType.DOUBLE);
    PRIMITIVE_TYPES.put(PsiType.FLOAT.getCanonicalText(), PsiType.FLOAT);
    PRIMITIVE_TYPES.put(PsiType.INT.getCanonicalText(), PsiType.INT);
    PRIMITIVE_TYPES.put(PsiType.LONG.getCanonicalText(), PsiType.LONG);
    PRIMITIVE_TYPES.put(PsiType.SHORT.getCanonicalText(), PsiType.SHORT);
    PRIMITIVE_TYPES.put(PsiType.BOOLEAN.getCanonicalText(), PsiType.BOOLEAN);
    PRIMITIVE_TYPES.put(PsiType.VOID.getCanonicalText(), PsiType.VOID);
    PRIMITIVE_TYPES.put(PsiType.NULL.getCanonicalText(), PsiType.NULL);
  }

  public PsiJavaParserFacadeImpl(final PsiManagerEx manager) {
    super(manager);
  }

  @NotNull
  @Override
  public PsiAnnotation createAnnotationFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, ANNOTATION, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiAnnotation)) {
      throw new IncorrectOperationException("Incorrect annotation \"" + text + "\".");
    }
    return (PsiAnnotation)element;
  }

  @NotNull
  @Override
  public PsiDocTag createDocTagFromText(@NotNull final String text) throws IncorrectOperationException {
    return createDocCommentFromText(StringUtil.join("/**\n", text, "\n */")).getTags()[0];
  }

  @NotNull
  @Override
  public PsiDocTag createDocTagFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    return createDocTagFromText(text);
  }

  @NotNull
  @Override
  public PsiDocComment createDocCommentFromText(@NotNull final String text) throws IncorrectOperationException {
    final PsiMethod method = createMethodFromText(StringUtil.join(text, "void m();"), null);
    final PsiDocComment comment = method.getDocComment();
    assert comment != null : text;
    return comment;
  }

  @NotNull
  @Override
  public PsiDocComment createDocCommentFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    return createDocCommentFromText(text);
  }

  @NotNull
  @Override
  public PsiClass createClassFromText(@NotNull final String body, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiJavaFile aFile = createDummyJavaFile(StringUtil.join("class _Dummy_ {\n", body, "\n}"));
    final PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException("Incorrect class \"" + body + "\".");
    }
    return classes[0];
  }

  @NotNull
  @Override
  public PsiField createFieldFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, DECLARATION, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiField)) {
      throw new IncorrectOperationException("Incorrect field \"" + text + "\".");
    }
    return (PsiField)element;
  }

  @NotNull
  @Override
  public PsiMethod createMethodFromText(@NotNull final String text, @Nullable final PsiElement context, final LanguageLevel level) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, DECLARATION, level), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiMethod)) {
      throw new IncorrectOperationException("Incorrect method \"" + text + "\" (" + element + ").");
    }
    return (PsiMethod)element;
  }

  @NotNull
  @Override
  public final PsiMethod createMethodFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final LanguageLevel level = LanguageLevelProjectExtension.getInstance(myManager.getProject()).getLanguageLevel();
    return createMethodFromText(text, context, level);
  }

  @NotNull
  @Override
  public PsiParameter createParameterFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, PARAMETER, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiParameter)) {
      throw new IncorrectOperationException("Incorrect parameter \"" + text + "\".");
    }
    return (PsiParameter)element;
  }

  @NotNull
  @Override
  public PsiResourceVariable createResourceFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, RESOURCE, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiResourceVariable)) {
      throw new IncorrectOperationException("Incorrect resource \"" + text + "\".");
    }
    return (PsiResourceVariable)element;
  }

  @NotNull
  @Override
  public PsiType createTypeFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    return createTypeInner(text, context, false);
  }
 
  @NotNull
  @Override
  public PsiTypeElement createTypeElementFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, TYPE, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiTypeElement)) {
      throw new IncorrectOperationException("Incorrect type \"" + text + "\".");
    }
    return (PsiTypeElement)element;
  }

  protected PsiType createTypeInner(final String text, @Nullable final PsiElement context, final boolean markAsCopy) throws IncorrectOperationException {
    final PsiPrimitiveType primitiveType = PRIMITIVE_TYPES.get(text);
    if (primitiveType != null) return primitiveType;

    final PsiTypeElement element = createTypeElementFromText(text, context);
    if (markAsCopy) {
      ((TreeElement)element.getNode()).acceptTree(new GeneratedMarkerVisitor());
    }
    return element.getType();
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceElement createReferenceFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final boolean isStaticImport = context instanceof PsiImportStaticStatement &&
                                   !((PsiImportStaticStatement)context).isOnDemand();
    final boolean mayHaveDiamonds = context instanceof PsiNewExpression &&
                                    PsiUtil.getLanguageLevel(context).isAtLeast(LanguageLevel.JDK_1_7);
    final JavaParserUtil.ParserWrapper wrapper = isStaticImport ? STATIC_IMPORT_REF : mayHaveDiamonds ? DIAMOND_REF : REFERENCE;
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, wrapper, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiJavaCodeReferenceElement)) {
      throw new IncorrectOperationException("Incorrect reference \"" + text + "\".");
    }
    return (PsiJavaCodeReferenceElement)element;
  }

  @NotNull
  @Override
  public PsiCodeBlock createCodeBlockFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, CODE_BLOCK, level(context), true), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiCodeBlock)) {
      throw new IncorrectOperationException("Incorrect code block \"" + text + "\".");
    }
    return (PsiCodeBlock)element;
  }

  @NotNull
  @Override
  public PsiStatement createStatementFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, STATEMENT, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiStatement)) {
      throw new IncorrectOperationException("Incorrect statement \"" + text + "\".");
    }
    return (PsiStatement)element;
  }

  @NotNull
  @Override
  public PsiExpression createExpressionFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, EXPRESSION, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiExpression)) {
      throw new IncorrectOperationException("Incorrect expression \"" + text + "\".");
    }
    return (PsiExpression)element;
  }

  protected PsiJavaFile createDummyJavaFile(final String text) {
    final String fileName = "_Dummy_." + StdFileTypes.JAVA.getDefaultExtension();
    final FileType type = StdFileTypes.JAVA;
    return (PsiJavaFile)PsiFileFactory.getInstance(myManager.getProject()).createFileFromText(type, fileName, text, 0, text.length());
  }

  @NotNull
  @Override
  public PsiTypeParameter createTypeParameterFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, TYPE_PARAMETER, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiTypeParameter)) {
      throw new IncorrectOperationException("Incorrect type parameter \"" + text + "\".");
    }
    return (PsiTypeParameter)element;
  }

  @NotNull
  @Override
  public PsiComment createCommentFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiJavaFile aFile = createDummyJavaFile(text);
    for (PsiElement aChildren : aFile.getChildren()) {
      if (aChildren instanceof PsiComment) {
        if (!aChildren.getText().equals(text)) {
          break;
        }
        final PsiComment comment = (PsiComment)aChildren;
        DummyHolderFactory.createHolder(myManager, (TreeElement)SourceTreeToPsiMap.psiElementToTree(comment), context);
        return comment;
      }
    }

    throw new IncorrectOperationException("Incorrect comment \"" + text + "\".");
  }

  @NotNull
  @Override
  public PsiEnumConstant createEnumConstantFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, ENUM_CONSTANT, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiEnumConstant)) {
      throw new IncorrectOperationException("Incorrect enum constant \"" + text + "\".");
    }
    return (PsiEnumConstant)element;
  }

  @NotNull
  @Override
  public PsiCatchSection createCatchSection(@NotNull final PsiType exceptionType,
                                            @NotNull final String exceptionName,
                                            @Nullable final PsiElement context) throws IncorrectOperationException {
    if (!(exceptionType instanceof PsiClassType || exceptionType instanceof PsiDisjunctionType)) {
      throw new IncorrectOperationException("Unexpected type:" + exceptionType);
    }
    final String text = StringUtil.join("catch (", exceptionType.getCanonicalText(), " ", exceptionName, ") {}");
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, CATCH_SECTION, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiCatchSection)) {
      throw new IncorrectOperationException("Incorrect catch section '" + text + "'. Parsed element: " + element);
    }
    setupCatchBlock(exceptionName, context, (PsiCatchSection)element);
    return (PsiCatchSection)myManager.getCodeStyleManager().reformat(element);
  }

  private void setupCatchBlock(final String exceptionName, @Nullable final PsiElement context, final PsiCatchSection psiCatchSection)
     throws IncorrectOperationException {
    final FileTemplate catchBodyTemplate = FileTemplateManager.getInstance().getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    LOG.assertTrue(catchBodyTemplate != null);

    final Properties props = new Properties();
    props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, exceptionName);
    if (context != null && context.isPhysical()) {
      final PsiDirectory directory = context.getContainingFile().getContainingDirectory();
      if (directory != null) {
        JavaTemplateUtil.setPackageNameAttribute(props, directory);
      }
    }

    final PsiCodeBlock codeBlockFromText;
    try {
      codeBlockFromText = createCodeBlockFromText("{\n" + catchBodyTemplate.getText(props) + "\n}", null);
    }
    catch (ProcessCanceledException ce) {
      throw ce;
    }
    catch (Exception e) {
      throw new IncorrectOperationException("Incorrect file template", e);
    }
    psiCatchSection.getCatchBlock().replace(codeBlockFromText);
  }

  @NotNull
  @Override
  public PsiType createPrimitiveType(@NotNull final String text, @NotNull final PsiAnnotation[] annotations) throws IncorrectOperationException {
    final PsiPrimitiveType primitiveType = getPrimitiveType(text);
    if (primitiveType == null) {
      throw new IncorrectOperationException("Incorrect primitive type \"" + text + "\".");
    }
    return annotations.length == 0 ? primitiveType : new PsiPrimitiveType(text, annotations);
  }

  public static PsiPrimitiveType getPrimitiveType(final String text) {
    return PRIMITIVE_TYPES.get(text);
  }

  private static LanguageLevel level(@Nullable final PsiElement context) {
    return context != null ? PsiUtil.getLanguageLevel(context) : LanguageLevel.HIGHEST;
  }
}
