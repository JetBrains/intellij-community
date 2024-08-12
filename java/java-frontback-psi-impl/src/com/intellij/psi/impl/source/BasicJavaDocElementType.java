package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.lexer.BasicJavaLexer;
import com.intellij.lang.java.lexer.JavaDocLexer;
import com.intellij.lang.java.parser.BasicJavaDocParser;
import com.intellij.lang.java.parser.BasicJavaParser;
import com.intellij.lang.java.parser.BasicJavaParserUtil;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.tree.*;
import com.intellij.psi.tree.java.IJavaDocElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public interface BasicJavaDocElementType {
  IElementType BASIC_DOC_TAG = new IJavaDocElementType("DOC_TAG");
  IElementType BASIC_DOC_INLINE_TAG = new IJavaDocElementType("DOC_INLINE_TAG");
  IElementType BASIC_DOC_METHOD_OR_FIELD_REF = new IJavaDocElementType("DOC_METHOD_OR_FIELD_REF");
  IElementType BASIC_DOC_PARAMETER_REF = new IJavaDocElementType("DOC_PARAMETER_REF");
  IElementType BASIC_DOC_TAG_VALUE_ELEMENT = new IJavaDocElementType("DOC_TAG_VALUE_ELEMENT");
  IElementType BASIC_DOC_SNIPPET_TAG = new IJavaDocElementType("DOC_SNIPPET_TAG");
  IElementType BASIC_DOC_SNIPPET_TAG_VALUE = new IJavaDocElementType("DOC_SNIPPET_TAG_VALUE");
  IElementType BASIC_DOC_SNIPPET_BODY = new IJavaDocElementType("DOC_SNIPPET_BODY");
  IElementType BASIC_DOC_SNIPPET_ATTRIBUTE = new IJavaDocElementType("DOC_SNIPPET_ATTRIBUTE");
  IElementType BASIC_DOC_SNIPPET_ATTRIBUTE_LIST =
    new IJavaDocElementType("DOC_SNIPPET_ATTRIBUTE_LIST");
  IElementType BASIC_DOC_SNIPPET_ATTRIBUTE_VALUE = new IJavaDocElementType("DOC_SNIPPET_ATTRIBUTE_VALUE");

  IElementType BASIC_DOC_REFERENCE_HOLDER = new IJavaDocElementType("DOC_REFERENCE_HOLDER");

  IElementType BASIC_DOC_TYPE_HOLDER = new IJavaDocElementType("DOC_TYPE_HOLDER");

  IElementType BASIC_DOC_COMMENT = new IJavaDocElementType("DOC_COMMENT");
  IElementType BASIC_DOC_MARKDOWN_CODE_BLOCK = new IJavaDocElementType("DOC_CODE_BLOCK");
  IElementType BASIC_DOC_MARKDOWN_REFERENCE_LINK = new IJavaDocElementType("DOC_REFERENCE_LINK");

  ParentAwareTokenSet BASIC_ALL_JAVADOC_ELEMENTS = ParentAwareTokenSet.create(
    BASIC_DOC_TAG, BASIC_DOC_INLINE_TAG, BASIC_DOC_METHOD_OR_FIELD_REF, BASIC_DOC_PARAMETER_REF, BASIC_DOC_TAG_VALUE_ELEMENT,
    BASIC_DOC_REFERENCE_HOLDER, BASIC_DOC_TYPE_HOLDER, BASIC_DOC_COMMENT);


  class JavaDocCompositeElementType extends IJavaDocElementType implements ICompositeElementType, ParentProviderElementType {
    private final Supplier<? extends ASTNode> myConstructor;
    private final Set<IElementType> myParentElementTypes;

    protected JavaDocCompositeElementType(@NonNls @NotNull String debugName,
                                          @NotNull Supplier<? extends ASTNode> nodeClass,
                                          @NotNull IElementType parentElementType) {
      super(debugName);
      myConstructor = nodeClass;
      myParentElementTypes = Collections.singleton(parentElementType);
    }

    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return myConstructor.get();
    }

    @Override
    public @NotNull Set<IElementType> getParents() {
      return myParentElementTypes;
    }
  }

  class JavaDocLazyElementType extends ILazyParseableElementType implements ParentProviderElementType {
    private final Set<IElementType> myParentElementTypes;

    private JavaDocLazyElementType(@NonNls final String debugName, @NotNull IElementType parentElementType) {
      super(debugName, JavaLanguage.INSTANCE);
      myParentElementTypes = Collections.singleton(parentElementType);
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new LazyParseablePsiElement(this, text);
    }

    @Override
    public @NotNull Set<IElementType> getParents() {
      return myParentElementTypes;
    }
  }


  final class DocReferenceHolderElementType extends JavaDocLazyElementType {

    private final @NotNull Supplier<? extends BasicJavaParser> myJavaThinParser;
    private final Function<LanguageLevel, JavaDocLexer> javaDocLexer;
    private final Function<LanguageLevel, BasicJavaLexer> javaLexer;


    public DocReferenceHolderElementType(@NotNull Supplier<? extends BasicJavaParser> parser,
                                         @NotNull Function<LanguageLevel, JavaDocLexer> docLexerFunction,
                                         @NotNull Function<LanguageLevel, BasicJavaLexer> javaLexer) {
      super("DOC_REFERENCE_HOLDER", BASIC_DOC_REFERENCE_HOLDER);
      this.myJavaThinParser = parser;
      this.javaDocLexer = docLexerFunction;
      this.javaLexer = javaLexer;
    }

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      BasicJavaParserUtil.ParserWrapper wrapper = builder -> BasicJavaDocParser.parseJavadocReference(builder,
                                                                                                      myJavaThinParser.get());
      return BasicJavaParserUtil.parseFragment(chameleon, wrapper, false, LanguageLevel.JDK_1_3, javaDocLexer, javaLexer
      );
    }
  }

  final class DocTypeHolderElementType extends JavaDocLazyElementType {

    private final @NotNull Supplier<? extends BasicJavaParser> myJavaThinParser;
    private final Function<LanguageLevel, JavaDocLexer> javaDocLexer;
    private final Function<LanguageLevel, BasicJavaLexer> javaLexer;

    public DocTypeHolderElementType(@NotNull Supplier<? extends BasicJavaParser> parser,
                                    @NotNull Function<LanguageLevel, JavaDocLexer> docLexerFunction,
                                    @NotNull Function<LanguageLevel, BasicJavaLexer> javaLexer) {
      super("DOC_TYPE_HOLDER", BASIC_DOC_TYPE_HOLDER);
      this.myJavaThinParser = parser;
      this.javaDocLexer = docLexerFunction;
      this.javaLexer = javaLexer;
    }

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      BasicJavaParserUtil.ParserWrapper wrapper = builder -> BasicJavaDocParser.parseJavadocType(builder, myJavaThinParser.get());
      return BasicJavaParserUtil.parseFragment(chameleon, wrapper, false, LanguageLevel.JDK_1_3, javaDocLexer, javaLexer
      );
    }
  }

  abstract class DocCommentElementType extends IReparseableElementType implements ParentProviderElementType {
    private final BasicJavaParserUtil.ParserWrapper myParser;
    private final AbstractBasicJavaDocElementTypeFactory myJavaDocElementTypeFactory;

    private final Function<LanguageLevel, JavaDocLexer> myDocLexerFunction;
    private final Function<LanguageLevel, BasicJavaLexer> myLexerFunction;

    private final Function<Project, Lexer> lexerByProject;

    private static final Set<IElementType> myParentElementTypes = Collections.singleton(BASIC_DOC_COMMENT);

    public DocCommentElementType(@NotNull Function<LanguageLevel, JavaDocLexer> function,
                                 @NotNull Function<LanguageLevel, BasicJavaLexer> lexerFunction,
                                 @NotNull AbstractBasicJavaDocElementTypeFactory factory,
                                 @NotNull Function<Project, Lexer> project) {
      super("DOC_COMMENT", JavaLanguage.INSTANCE);
      myParser = builder -> BasicJavaDocParser.parseDocCommentText(builder, factory.getContainer());
      myJavaDocElementTypeFactory = factory;
      myDocLexerFunction = function;
      myLexerFunction = lexerFunction;
      lexerByProject = project;
    }

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      return BasicJavaParserUtil.parseFragment(chameleon, myParser, myDocLexerFunction, myLexerFunction);
    }

    @Override
    public boolean isParsable(@NotNull final CharSequence buffer, @NotNull Language fileLanguage, @NotNull final Project project) {
      if (!StringUtil.startsWith(buffer, "/**") || !StringUtil.endsWith(buffer, "*/")) return false;

      Lexer lexer = lexerByProject.apply(project);
      lexer.start(buffer);
      if (lexer.getTokenType() == myJavaDocElementTypeFactory.getContainer().DOC_COMMENT) {
        lexer.advance();
        return lexer.getTokenType() == null;
      }
      return false;
    }

    @Override
    public @NotNull Set<IElementType> getParents() {
      return myParentElementTypes;
    }
  }
}