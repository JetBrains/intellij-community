package com.intellij.psi.impl.source;

import com.intellij.lang.*;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.lexer.BasicJavaLexer;
import com.intellij.lang.java.lexer.JavaDocLexer;
import com.intellij.lang.java.parser.BasicJavaParser;
import com.intellij.lang.java.parser.BasicJavaParserUtil;
import com.intellij.lang.java.parser.BasicReferenceParser;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.TokenList;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ICodeFragmentElementType;
import com.intellij.psi.tree.*;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;


public interface BasicJavaElementType {

  IElementType BASIC_CLASS = new IJavaElementType("CLASS");
  IElementType BASIC_UNNAMED_CLASS = new IJavaElementType("UNNAMED_CLASS");
  IElementType BASIC_ANONYMOUS_CLASS = new IJavaElementType("ANONYMOUS_CLASS");
  IElementType BASIC_ENUM_CONSTANT_INITIALIZER = new IJavaElementType("ENUM_CONSTANT_INITIALIZER");
  IElementType BASIC_TYPE_PARAMETER_LIST = new IJavaElementType("TYPE_PARAMETER_LIST", true);
  IElementType BASIC_TYPE_PARAMETER = new IJavaElementType("TYPE_PARAMETER");
  IElementType BASIC_IMPORT_LIST = new IJavaElementType("IMPORT_LIST");
  IElementType BASIC_IMPORT_STATEMENT = new IJavaElementType("IMPORT_STATEMENT");
  IElementType BASIC_IMPORT_STATIC_STATEMENT = new IJavaElementType("IMPORT_STATIC_STATEMENT");
  IElementType BASIC_MODIFIER_LIST = new IJavaElementType("MODIFIER_LIST");
  IElementType BASIC_ANNOTATION = new IJavaElementType("ANNOTATION");
  IElementType BASIC_NAME_VALUE_PAIR = new IJavaElementType("NAME_VALUE_PAIR", true);
  IElementType BASIC_LITERAL_EXPRESSION = new IJavaElementType("LITERAL_EXPRESSION");
  IElementType BASIC_ANNOTATION_PARAMETER_LIST = new IJavaElementType("ANNOTATION_PARAMETER_LIST", true);
  IElementType BASIC_EXTENDS_LIST = new IJavaElementType("EXTENDS_LIST", true);
  IElementType BASIC_IMPLEMENTS_LIST = new IJavaElementType("IMPLEMENTS_LIST", true);
  IElementType BASIC_FIELD = new IJavaElementType("FIELD");
  IElementType BASIC_ENUM_CONSTANT = new IJavaElementType("ENUM_CONSTANT");
  IElementType BASIC_METHOD = new IJavaElementType("METHOD");
  IElementType BASIC_ANNOTATION_METHOD = new IJavaElementType("ANNOTATION_METHOD");
  IElementType BASIC_CLASS_INITIALIZER = new IJavaElementType("CLASS_INITIALIZER");
  IElementType BASIC_PARAMETER = new IJavaElementType("PARAMETER");
  IElementType BASIC_PARAMETER_LIST = new IJavaElementType("PARAMETER_LIST");
  IElementType BASIC_EXTENDS_BOUND_LIST = new IJavaElementType("EXTENDS_BOUND_LIST", true);
  IElementType BASIC_THROWS_LIST = new IJavaElementType("THROWS_LIST", true);
  IElementType BASIC_LAMBDA_EXPRESSION = new IJavaElementType("LAMBDA_EXPRESSION");
  IElementType BASIC_METHOD_REF_EXPRESSION = new IJavaElementType("METHOD_REF_EXPRESSION");
  IElementType BASIC_MODULE = new IJavaElementType("MODULE");
  IElementType BASIC_REQUIRES_STATEMENT = new IJavaElementType("REQUIRES_STATEMENT");
  IElementType BASIC_EXPORTS_STATEMENT = new IJavaElementType("EXPORTS_STATEMENT");
  IElementType BASIC_OPENS_STATEMENT = new IJavaElementType("OPENS_STATEMENT");
  IElementType BASIC_USES_STATEMENT = new IJavaElementType("USES_STATEMENT");
  IElementType BASIC_PROVIDES_STATEMENT = new IJavaElementType("PROVIDES_STATEMENT");
  IElementType BASIC_PROVIDES_WITH_LIST = new IJavaElementType("PROVIDES_WITH_LIST", true);
  IElementType BASIC_RECORD_COMPONENT = new IJavaElementType("RECORD_COMPONENT");
  IElementType BASIC_RECORD_HEADER = new IJavaElementType("RECORD_HEADER");
  IElementType BASIC_PERMITS_LIST = new IJavaElementType("PERMITS_LIST", true);

  IElementType BASIC_IMPORT_STATIC_REFERENCE = new IJavaElementType("IMPORT_STATIC_REFERENCE");
  IElementType BASIC_TYPE = new IJavaElementType("TYPE");
  IElementType BASIC_DIAMOND_TYPE = new IJavaElementType("DIAMOND_TYPE");
  IElementType BASIC_REFERENCE_PARAMETER_LIST = new IJavaElementType("REFERENCE_PARAMETER_LIST", true);
  IElementType BASIC_JAVA_CODE_REFERENCE = new IJavaElementType("JAVA_CODE_REFERENCE");
  IElementType BASIC_PACKAGE_STATEMENT = new IJavaElementType("PACKAGE_STATEMENT");
  IElementType BASIC_LOCAL_VARIABLE = new IJavaElementType("LOCAL_VARIABLE");
  IElementType BASIC_REFERENCE_EXPRESSION = new IJavaElementType("REFERENCE_EXPRESSION");
  IElementType BASIC_THIS_EXPRESSION = new IJavaElementType("THIS_EXPRESSION");
  IElementType BASIC_SUPER_EXPRESSION = new IJavaElementType("SUPER_EXPRESSION");
  IElementType BASIC_PARENTH_EXPRESSION = new IJavaElementType("PARENTH_EXPRESSION");
  IElementType BASIC_METHOD_CALL_EXPRESSION = new IJavaElementType("METHOD_CALL_EXPRESSION");
  IElementType BASIC_TYPE_CAST_EXPRESSION = new IJavaElementType("TYPE_CAST_EXPRESSION");
  IElementType BASIC_PREFIX_EXPRESSION = new IJavaElementType("PREFIX_EXPRESSION");
  IElementType BASIC_POSTFIX_EXPRESSION = new IJavaElementType("POSTFIX_EXPRESSION");
  IElementType BASIC_BINARY_EXPRESSION = new IJavaElementType("BINARY_EXPRESSION");
  IElementType BASIC_POLYADIC_EXPRESSION = new IJavaElementType("POLYADIC_EXPRESSION");
  IElementType BASIC_CONDITIONAL_EXPRESSION = new IJavaElementType("CONDITIONAL_EXPRESSION");
  IElementType BASIC_ASSIGNMENT_EXPRESSION = new IJavaElementType("ASSIGNMENT_EXPRESSION");
  IElementType BASIC_NEW_EXPRESSION = new IJavaElementType("NEW_EXPRESSION");
  IElementType BASIC_ARRAY_ACCESS_EXPRESSION = new IJavaElementType("ARRAY_ACCESS_EXPRESSION");
  IElementType BASIC_ARRAY_INITIALIZER_EXPRESSION =
    new IJavaElementType("ARRAY_INITIALIZER_EXPRESSION");
  IElementType BASIC_INSTANCE_OF_EXPRESSION = new IJavaElementType("INSTANCE_OF_EXPRESSION");
  IElementType BASIC_CLASS_OBJECT_ACCESS_EXPRESSION =
    new IJavaElementType("CLASS_OBJECT_ACCESS_EXPRESSION");

  IElementType BASIC_TEMPLATE_EXPRESSION = new IJavaElementType("TEMPLATE_EXPRESSION");

  IElementType BASIC_TEMPLATE = new IJavaElementType("TEMPLATE");

  IElementType BASIC_EMPTY_EXPRESSION = new IJavaElementType("EMPTY_EXPRESSION", true);
  IElementType BASIC_EXPRESSION_LIST = new IJavaElementType("EXPRESSION_LIST", true);
  IElementType BASIC_EMPTY_STATEMENT = new IJavaElementType("EMPTY_STATEMENT");
  IElementType BASIC_BLOCK_STATEMENT = new IJavaElementType("BLOCK_STATEMENT");
  IElementType BASIC_EXPRESSION_STATEMENT = new IJavaElementType("EXPRESSION_STATEMENT");
  IElementType BASIC_EXPRESSION_LIST_STATEMENT =
    new IJavaElementType("EXPRESSION_LIST_STATEMENT");
  IElementType BASIC_DECLARATION_STATEMENT = new IJavaElementType("DECLARATION_STATEMENT");
  IElementType BASIC_IF_STATEMENT = new IJavaElementType("IF_STATEMENT");
  IElementType BASIC_WHILE_STATEMENT = new IJavaElementType("WHILE_STATEMENT");
  IElementType BASIC_FOR_STATEMENT = new IJavaElementType("FOR_STATEMENT");
  IElementType BASIC_FOREACH_STATEMENT = new IJavaElementType("FOREACH_STATEMENT");
  IElementType BASIC_FOREACH_PATTERN_STATEMENT =
    new IJavaElementType("FOREACH_PATTERN_STATEMENT");
  IElementType BASIC_DO_WHILE_STATEMENT = new IJavaElementType("DO_WHILE_STATEMENT");
  IElementType BASIC_SWITCH_STATEMENT = new IJavaElementType("SWITCH_STATEMENT");
  IElementType BASIC_SWITCH_EXPRESSION = new IJavaElementType("SWITCH_EXPRESSION");
  IElementType BASIC_SWITCH_LABEL_STATEMENT = new IJavaElementType("SWITCH_LABEL_STATEMENT");
  IElementType BASIC_SWITCH_LABELED_RULE = new IJavaElementType("SWITCH_LABELED_RULE");
  IElementType BASIC_BREAK_STATEMENT = new IJavaElementType("BREAK_STATEMENT");
  IElementType BASIC_YIELD_STATEMENT = new IJavaElementType("YIELD_STATEMENT");
  IElementType BASIC_CONTINUE_STATEMENT = new IJavaElementType("CONTINUE_STATEMENT");
  IElementType BASIC_RETURN_STATEMENT = new IJavaElementType("RETURN_STATEMENT");
  IElementType BASIC_THROW_STATEMENT = new IJavaElementType("THROW_STATEMENT");
  IElementType BASIC_SYNCHRONIZED_STATEMENT = new IJavaElementType("SYNCHRONIZED_STATEMENT");
  IElementType BASIC_TRY_STATEMENT = new IJavaElementType("TRY_STATEMENT");
  IElementType BASIC_RESOURCE_LIST = new IJavaElementType("RESOURCE_LIST");
  IElementType BASIC_RESOURCE_VARIABLE = new IJavaElementType("RESOURCE_VARIABLE");
  IElementType BASIC_RESOURCE_EXPRESSION = new IJavaElementType("RESOURCE_EXPRESSION");
  IElementType BASIC_CATCH_SECTION = new IJavaElementType("CATCH_SECTION");
  IElementType BASIC_LABELED_STATEMENT = new IJavaElementType("LABELED_STATEMENT");
  IElementType BASIC_ASSERT_STATEMENT = new IJavaElementType("ASSERT_STATEMENT");
  IElementType BASIC_ANNOTATION_ARRAY_INITIALIZER =
    new IJavaElementType("ANNOTATION_ARRAY_INITIALIZER");
  IElementType BASIC_RECEIVER_PARAMETER = new IJavaElementType("RECEIVER");
  IElementType BASIC_MODULE_REFERENCE = new IJavaElementType("MODULE_REFERENCE");
  IElementType BASIC_TYPE_TEST_PATTERN = new IJavaElementType("TYPE_TEST_PATTERN");
  IElementType BASIC_UNNAMED_PATTERN = new IJavaElementType("UNNAMED_PATTERN");
  IElementType BASIC_PATTERN_VARIABLE = new IJavaElementType("PATTERN_VARIABLE");
  IElementType BASIC_DECONSTRUCTION_PATTERN = new IJavaElementType("DECONSTRUCTION_PATTERN");
  IElementType BASIC_DECONSTRUCTION_LIST = new IJavaElementType("DECONSTRUCTION_LIST");
  IElementType BASIC_DECONSTRUCTION_PATTERN_VARIABLE =
    new IJavaElementType("DECONSTRUCTION_PATTERN_VARIABLE");
  IElementType BASIC_PARENTHESIZED_PATTERN = new IJavaElementType("PARENTHESIZED_PATTERN");
  IElementType BASIC_DEFAULT_CASE_LABEL_ELEMENT =
    new IJavaElementType("DEFAULT_CASE_LABEL_ELEMENT");
  IElementType BASIC_CASE_LABEL_ELEMENT_LIST = new IJavaElementType("CASE_LABEL_ELEMENT_LIST");

  IElementType BASIC_CODE_BLOCK = new IElementType("CODE_BLOCK", JavaLanguage.INSTANCE);

  IElementType BASIC_MEMBERS = new IElementType("MEMBERS", JavaLanguage.INSTANCE);

  IElementType BASIC_STATEMENTS = new IElementType("STATEMENTS", JavaLanguage.INSTANCE);
  IElementType BASIC_EXPRESSION_TEXT = new IElementType("EXPRESSION_TEXT", JavaLanguage.INSTANCE);
  IElementType BASIC_REFERENCE_TEXT = new IElementType("REFERENCE_TEXT", JavaLanguage.INSTANCE);
  IElementType BASIC_TYPE_WITH_DISJUNCTIONS_TEXT = new IElementType("TYPE_WITH_DISJUNCTIONS_TEXT", JavaLanguage.INSTANCE);
  IElementType BASIC_TYPE_WITH_CONJUNCTIONS_TEXT = new IElementType("TYPE_WITH_CONJUNCTIONS_TEXT", JavaLanguage.INSTANCE);
  IElementType BASIC_DUMMY_ELEMENT = new IJavaElementType("DUMMY_ELEMENT");

  ParentAwareTokenSet STATEMENT_SET =
    ParentAwareTokenSet.create(
      BASIC_ASSERT_STATEMENT, BASIC_BLOCK_STATEMENT, BASIC_BREAK_STATEMENT, BASIC_CONTINUE_STATEMENT,
      BASIC_DECLARATION_STATEMENT, BASIC_DO_WHILE_STATEMENT, BASIC_EMPTY_STATEMENT, BASIC_EXPRESSION_LIST_STATEMENT,
      BASIC_EXPRESSION_STATEMENT,
      BASIC_FOREACH_PATTERN_STATEMENT, BASIC_FOREACH_STATEMENT, BASIC_FOR_STATEMENT, BASIC_IF_STATEMENT,
      BASIC_LABELED_STATEMENT,
      BASIC_RETURN_STATEMENT, BASIC_SWITCH_LABELED_RULE, BASIC_SWITCH_LABEL_STATEMENT, BASIC_SWITCH_STATEMENT,
      BASIC_SYNCHRONIZED_STATEMENT,
      BASIC_THROW_STATEMENT,
      BASIC_TRY_STATEMENT, BASIC_WHILE_STATEMENT, BASIC_YIELD_STATEMENT,
      BASIC_USES_STATEMENT, BASIC_REQUIRES_STATEMENT, BASIC_PROVIDES_STATEMENT, BASIC_EXPORTS_STATEMENT);

  IElementType BASIC_JAVA_CODE_REFERENCE_ELEMENT_MARK = new IElementType("BASIC_JAVA_CODE_REFERENCE_ELEMENT_MARK", JavaLanguage.INSTANCE);

  ParentAwareTokenSet JAVA_CODE_REFERENCE_ELEMENT_SET = ParentAwareTokenSet.create(
    BASIC_JAVA_CODE_REFERENCE_ELEMENT_MARK,
    BASIC_IMPORT_STATIC_REFERENCE, BASIC_JAVA_CODE_REFERENCE, BASIC_METHOD_REF_EXPRESSION, BASIC_REFERENCE_EXPRESSION
  );

  IElementType BASIC_REFERENCE_EXPRESSION_MARK = new IElementType("BASIC_REFERENCE_EXPRESSION_MARK", JavaLanguage.INSTANCE);

  ParentAwareTokenSet REFERENCE_EXPRESSION_SET = ParentAwareTokenSet.create(
    BASIC_REFERENCE_EXPRESSION_MARK,
    BASIC_METHOD_REF_EXPRESSION, BASIC_REFERENCE_EXPRESSION
  );

  IElementType BASIC_EXPRESSION_MARK = new IElementType("BASIC_EXPRESSION_MARK", JavaLanguage.INSTANCE);

  ParentAwareTokenSet EXPRESSION_SET = ParentAwareTokenSet.create(
    BASIC_EXPRESSION_MARK,
    BASIC_REFERENCE_EXPRESSION, BASIC_LITERAL_EXPRESSION, BASIC_THIS_EXPRESSION, BASIC_SUPER_EXPRESSION,
    BASIC_PARENTH_EXPRESSION, BASIC_METHOD_CALL_EXPRESSION,
    BASIC_TYPE_CAST_EXPRESSION, BASIC_PREFIX_EXPRESSION, BASIC_POSTFIX_EXPRESSION, BASIC_BINARY_EXPRESSION,
    BASIC_POLYADIC_EXPRESSION, BASIC_CONDITIONAL_EXPRESSION,
    BASIC_ASSIGNMENT_EXPRESSION, BASIC_NEW_EXPRESSION, BASIC_ARRAY_ACCESS_EXPRESSION,
    BASIC_ARRAY_INITIALIZER_EXPRESSION, BASIC_INSTANCE_OF_EXPRESSION,
    BASIC_CLASS_OBJECT_ACCESS_EXPRESSION, BASIC_METHOD_REF_EXPRESSION, BASIC_LAMBDA_EXPRESSION, BASIC_SWITCH_EXPRESSION,
    BASIC_EMPTY_EXPRESSION,
    BASIC_TEMPLATE_EXPRESSION
  );

  IElementType BASIC_CLASS_MARK = new IElementType("BASIC_CLASS_MARK", JavaLanguage.INSTANCE);

  ParentAwareTokenSet CLASS_SET = ParentAwareTokenSet.create(BASIC_CLASS_MARK,
                                                             BASIC_CLASS, BASIC_ANONYMOUS_CLASS,
                                                             BASIC_ENUM_CONSTANT_INITIALIZER);
  IElementType BASIC_MEMBER_MARK = new IElementType("BASIC_MEMBER_MARK", JavaLanguage.INSTANCE);

  ParentAwareTokenSet MEMBER_SET = ParentAwareTokenSet.create(BASIC_MEMBER_MARK,
                                                              BASIC_ANNOTATION_METHOD, BASIC_ANONYMOUS_CLASS, BASIC_CLASS,
                                                              BASIC_CLASS_INITIALIZER,
                                                              BASIC_ENUM_CONSTANT, BASIC_ENUM_CONSTANT_INITIALIZER, BASIC_FIELD,
                                                              BASIC_METHOD, BASIC_RECORD_COMPONENT,
                                                              BASIC_TYPE_PARAMETER);


  class JavaCompositeElementType extends IJavaElementType implements ICompositeElementType, ParentProviderElementType {
    private final Supplier<? extends ASTNode> myConstructor;
    private final Set<IElementType> myParentElementTypes;

    public JavaCompositeElementType(@NonNls @NotNull String debugName,
                                    @NotNull Supplier<? extends ASTNode> constructor,
                                    @NotNull IElementType parentElementType) {
      this(debugName, constructor, false, parentElementType);
    }

    public JavaCompositeElementType(@NonNls @NotNull String debugName,
                                    @NotNull Supplier<? extends ASTNode> constructor,
                                    boolean leftBound,
                                    @NotNull IElementType parentElementType) {
      super(debugName, leftBound);
      myConstructor = constructor;
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

  final class JavaDummyElementType extends ILazyParseableElementType implements ICompositeElementType, ParentProviderElementType {
    private static final Set<IElementType> PARENT_ELEMENT_TYPES = Collections.singleton(BASIC_DUMMY_ELEMENT);
    private final Function<LanguageLevel, JavaDocLexer> javaDocLexer;
    private final Function<LanguageLevel, BasicJavaLexer> javaLexer;

    public JavaDummyElementType(@NotNull Function<LanguageLevel, JavaDocLexer> lexer,
                                @NotNull Function<LanguageLevel, BasicJavaLexer> javaLexer) {
      super("DUMMY_ELEMENT", JavaLanguage.INSTANCE);
      javaDocLexer = lexer;
      this.javaLexer = javaLexer;
    }

    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new CompositePsiElement(this) {
      };
    }

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      assert chameleon instanceof BasicJavaDummyElement : chameleon;
      final BasicJavaDummyElement dummyElement = (BasicJavaDummyElement)chameleon;
      return BasicJavaParserUtil.parseFragment(chameleon, dummyElement.getParser(), dummyElement.consumeAll(),
                                               dummyElement.getLanguageLevel(),
                                               javaDocLexer, javaLexer);
    }

    @Override
    public @NotNull Set<IElementType> getParents() {
      return PARENT_ELEMENT_TYPES;
    }
  }


  abstract class FrontBackICodeBlockElementType extends IErrorCounterReparseableElementType
    implements ICompositeElementType, ILightLazyParseableElementType, ParentProviderElementType {
    private static final Set<IElementType> PARENT_ELEMENT_TYPES = Collections.singleton(BASIC_CODE_BLOCK);
    private final @NotNull Supplier<? extends BasicJavaParser> myJavaThinParser;
    private final Function<PsiElement, LanguageLevel> languageLevelFunction;
    private final Function<LanguageLevel, BasicJavaLexer> myLexerFunction;
    private final Function<PsiFile, TokenList> psiAsLexer;

    public FrontBackICodeBlockElementType(@NotNull Supplier<? extends BasicJavaParser> parser,
                                          @NotNull Function<PsiElement, LanguageLevel> function,
                                          @NotNull Function<LanguageLevel, BasicJavaLexer> lexerFunction,
                                          @NotNull Function<PsiFile, TokenList> lexer) {
      super("CODE_BLOCK", JavaLanguage.INSTANCE);
      myJavaThinParser = parser;
      languageLevelFunction = function;
      myLexerFunction = lexerFunction;
      psiAsLexer = lexer;
    }

    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      final PsiBuilder builder = BasicJavaParserUtil.createBuilder(chameleon, languageLevelFunction, myLexerFunction, psiAsLexer);
      myJavaThinParser.get().getStatementParser().parseCodeBlockDeep(builder, true);
      return builder.getTreeBuilt().getFirstChildNode();
    }

    @Override
    public @NotNull FlyweightCapableTreeStructure<LighterASTNode> parseContents(final @NotNull LighterLazyParseableNode chameleon) {
      final PsiBuilder builder = BasicJavaParserUtil.createBuilder(chameleon, languageLevelFunction, myLexerFunction);
      myJavaThinParser.get().getStatementParser().parseCodeBlockDeep(builder, true);
      return builder.getLightTree();
    }

    @Override
    public int getErrorsCount(final CharSequence seq, Language fileLanguage, final Project project) {
      Lexer lexer = myLexerFunction.apply(LanguageLevel.HIGHEST);
      return PsiBuilderUtil.hasProperBraceBalance(seq, lexer, JavaTokenType.LBRACE, JavaTokenType.RBRACE) ? NO_ERRORS : FATAL_ERROR;
    }

    @Override
    public boolean reuseCollapsedTokens() {
      return true;
    }

    @Override
    public @NotNull Set<IElementType> getParents() {
      return PARENT_ELEMENT_TYPES;
    }
  }


  class FrontBackTypeTextElementType extends ICodeFragmentElementType implements ParentProviderElementType {
    private final int myFlags;
    private final @NotNull Supplier<? extends BasicJavaParser> myParserInstance;

    private final Function<LanguageLevel, JavaDocLexer> myDocLexerFunction;
    private final Function<LanguageLevel, BasicJavaLexer> myLexerFunction;
    private final Set<IElementType> myParentElementTypes;

    public FrontBackTypeTextElementType(@NonNls String debugName, int flags,
                                        @NotNull Supplier<? extends BasicJavaParser> parser,
                                        @NotNull Function<LanguageLevel, JavaDocLexer> docLexerFunction,
                                        @NotNull Function<LanguageLevel, BasicJavaLexer> lexerFunction,
                                        @NotNull IElementType parentElementType) {
      super(debugName, JavaLanguage.INSTANCE);
      myFlags = flags;
      myParserInstance = parser;
      myDocLexerFunction = docLexerFunction;
      myLexerFunction = lexerFunction;
      myParentElementTypes = Collections.singleton(parentElementType);
    }

    private final BasicJavaParserUtil.ParserWrapper myParser = new BasicJavaParserUtil.ParserWrapper() {
      @Override
      public void parse(final PsiBuilder builder) {
        int flags = BasicReferenceParser.EAT_LAST_DOT | BasicReferenceParser.ELLIPSIS | BasicReferenceParser.WILDCARD | myFlags;
        myParserInstance.get().getReferenceParser().parseType(builder, flags);
      }
    };

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      return BasicJavaParserUtil.parseFragment(chameleon, myParser, myDocLexerFunction, myLexerFunction);
    }

    @Override
    public @NotNull Set<IElementType> getParents() {
      return myParentElementTypes;
    }
  }

  class StatementThinCodeFragmentElementType extends ThinCodeFragmentElementType {

    public StatementThinCodeFragmentElementType(@NotNull Supplier<? extends BasicJavaParser> javaThinParser,
                                                @NotNull Function<LanguageLevel, JavaDocLexer> docLexerFunction,
                                                @NotNull Function<LanguageLevel, BasicJavaLexer> lexerFunction) {
      super("STATEMENTS", builder -> javaThinParser.get().getStatementParser().parseStatements(builder), docLexerFunction, lexerFunction,
            BASIC_STATEMENTS);
    }
  }

  class MemberThinCodeFragmentElementType extends ThinCodeFragmentElementType {

    public MemberThinCodeFragmentElementType(@NotNull Supplier<? extends BasicJavaParser> javaThinParser,
                                             @NotNull Function<LanguageLevel, JavaDocLexer> docLexerFunction,
                                             @NotNull Function<LanguageLevel, BasicJavaLexer> lexerFunction) {
      super("MEMBERS", builder -> javaThinParser.get().getDeclarationParser().parseClassBodyDeclarations(builder, false), docLexerFunction,
            lexerFunction,
            BASIC_MEMBERS);
    }
  }

  class ExpressionThinCodeFragmentElementType extends ThinCodeFragmentElementType {

    public ExpressionThinCodeFragmentElementType(@NotNull Supplier<? extends BasicJavaParser> javaThinParser,
                                                 @NotNull Function<LanguageLevel, JavaDocLexer> docLexerFunction,
                                                 @NotNull Function<LanguageLevel, BasicJavaLexer> lexerFunction) {
      super("EXPRESSION_TEXT", builder -> javaThinParser.get().getExpressionParser().parse(builder), docLexerFunction, lexerFunction,
            BASIC_EXPRESSION_TEXT);
    }
  }

  class ReferenceThinCodeFragmentElementType extends ThinCodeFragmentElementType {

    public ReferenceThinCodeFragmentElementType(@NotNull Supplier<? extends BasicJavaParser> javaThinParser,
                                                @NotNull Function<LanguageLevel, JavaDocLexer> docLexerFunction,
                                                @NotNull Function<LanguageLevel, BasicJavaLexer> lexerFunction) {
      super("REFERENCE_TEXT",
            builder -> javaThinParser.get().getReferenceParser().parseJavaCodeReference(builder, false, true, false, false),
            docLexerFunction, lexerFunction,
            BASIC_REFERENCE_TEXT);
    }
  }

  class ThinCodeFragmentElementType extends ICodeFragmentElementType implements ParentProviderElementType {
    private final BasicJavaParserUtil.ParserWrapper myParser;
    private final Function<LanguageLevel, JavaDocLexer> javaDocLexer;
    private final Function<LanguageLevel, BasicJavaLexer> javaLexer;
    private final Set<IElementType> parentElementTypes;

    private ThinCodeFragmentElementType(@NotNull String debugName,
                                        @NotNull BasicJavaParserUtil.ParserWrapper parser,
                                        @NotNull Function<LanguageLevel, JavaDocLexer> docLexerFunction,
                                        @NotNull Function<LanguageLevel, BasicJavaLexer> lexerFunction,
                                        @NotNull IElementType parentElementType) {
      super(debugName, JavaLanguage.INSTANCE);
      myParser = parser;
      javaDocLexer = docLexerFunction;
      this.javaLexer = lexerFunction;
      this.parentElementTypes = Collections.singleton(parentElementType);
    }

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      return BasicJavaParserUtil.parseFragment(chameleon, myParser, javaDocLexer, javaLexer);
    }

    @Override
    public @NotNull Set<IElementType> getParents() {
      return parentElementTypes;
    }
  }
}