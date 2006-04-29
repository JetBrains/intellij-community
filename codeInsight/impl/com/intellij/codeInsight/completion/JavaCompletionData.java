package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.classes.*;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.element.ReferenceOnFilter;
import com.intellij.psi.filters.getters.UpWalkGetter;
import com.intellij.psi.filters.position.*;
import com.intellij.psi.filters.types.TypeCodeFragmentIsVoidEnabledFilter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.jsp.JspElementType;
import org.jetbrains.annotations.NonNls;

public class JavaCompletionData extends CompletionData{

  protected static final @NonNls String[] MODIFIERS_LIST = {
    "public", "protected", "private",
    "static", "final", "native",
    "abstract", "synchronized", "volatile", "transient"
  };

  private static final @NonNls String[] ourBlockFinalizers = {"{", "}", ";", ":", "else"};

  public JavaCompletionData(){
    declareCompletionSpaces();

    final CompletionVariant variant = new CompletionVariant(PsiMethod.class, TrueFilter.INSTANCE);
    variant.includeScopeClass(PsiVariable.class);
    variant.includeScopeClass(PsiClass.class);
    variant.includeScopeClass(PsiFile.class);

    variant.addCompletion(new ModifierChooser());

    registerVariant(variant);

    initVariantsInFileScope();
    initVariantsInClassScope();
    initVariantsInMethodScope();
    initVariantsInFieldScope();

    defineScopeEquivalence(PsiMethod.class, PsiClassInitializer.class);
    defineScopeEquivalence(PsiMethod.class, PsiCodeFragment.class);
  }

  private void declareCompletionSpaces() {
    declareFinalScope(PsiFile.class);

    {
      // Class body
      final CompletionVariant variant = new CompletionVariant(CLASS_BODY);
      variant.includeScopeClass(PsiClass.class, true);
      registerVariant(variant);
    }
    {
      // Method body
      final CompletionVariant variant = new CompletionVariant(new InsideElementFilter(new ClassFilter(PsiCodeBlock.class)));
      variant.includeScopeClass(PsiMethod.class, true);
      variant.includeScopeClass(PsiClassInitializer.class, true);
      registerVariant(variant);
    }

    {
      // Field initializer
      final CompletionVariant variant = new CompletionVariant(new AfterElementFilter(new TextFilter("=")));
      variant.includeScopeClass(PsiField.class, true);
      registerVariant(variant);
    }

    declareFinalScope(PsiLiteralExpression.class);
    declareFinalScope(PsiComment.class);
  }

  protected void initVariantsInFileScope(){
// package keyword completion
    {
      final CompletionVariant variant = new CompletionVariant(PsiJavaFile.class, new StartElementFilter());
      variant.addCompletion(PsiKeyword.PACKAGE);
      registerVariant(variant);
    }

// import keyword completion
    {
      final CompletionVariant variant = new CompletionVariant(PsiJavaFile.class, new OrFilter(
        new StartElementFilter(),
        END_OF_BLOCK
      ));
      variant.addCompletion(PsiKeyword.IMPORT);

      registerVariant(variant);
    }
// other in file scope
    {
      final ElementFilter position = new OrFilter(
          END_OF_BLOCK,
          new LeftNeighbour(new OrFilter(new SuperParentFilter(new ClassFilter(PsiAnnotation.class)),
                                         new TextFilter(MODIFIERS_LIST))),
          new StartElementFilter());

      final CompletionVariant variant = new CompletionVariant(PsiJavaFile.class, position);
      variant.includeScopeClass(PsiClass.class);

      variant.addCompletion(PsiKeyword.CLASS);
      variant.addCompletion(PsiKeyword.INTERFACE);

      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(PsiTypeCodeFragment.class, new StartElementFilter());
      addPrimitiveTypes(variant, TailType.NONE);
      final CompletionVariant variant1 = new CompletionVariant(PsiTypeCodeFragment.class,
                                                               new AndFilter(
                                                                 new StartElementFilter(),
                                                                 new TypeCodeFragmentIsVoidEnabledFilter()
                                                               )
                                                               );
      variant1.addCompletion(PsiKeyword.VOID, TailType.NONE);
      registerVariant(variant);
      registerVariant(variant1);

    }

  }

  /**
   * aClass == null for JspDeclaration scope
   */
  protected void initVariantsInClassScope() {
// Completion for extends keyword
// position
    {
      final ElementFilter position = new AndFilter(
          new NotFilter(CLASS_BODY),
          new NotFilter(new AfterElementFilter(new ContentFilter(new TextFilter(PsiKeyword.EXTENDS)))),
          new NotFilter(new AfterElementFilter(new ContentFilter(new TextFilter(PsiKeyword.IMPLEMENTS)))),
          new NotFilter(new LeftNeighbour(new LeftNeighbour(new TextFilter("<", ",")))),
          new NotFilter(new ScopeFilter(new EnumOrAnnotationTypeFilter())),
          new LeftNeighbour(new OrFilter(
            new ClassFilter(PsiIdentifier.class),
            new TextFilter(">"))));
// completion
      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiClass.class, true);
      variant.addCompletion(PsiKeyword.EXTENDS);
      variant.excludeScopeClass(PsiAnonymousClass.class);
      variant.excludeScopeClass(PsiTypeParameter.class);

      registerVariant(variant);
    }
// Completion for implements keyword
// position
    {
      final ElementFilter position = new AndFilter(
          new NotFilter(CLASS_BODY),
          new NotFilter(new BeforeElementFilter(new ContentFilter(new TextFilter(PsiKeyword.EXTENDS)))),
          new NotFilter(new AfterElementFilter(new ContentFilter(new TextFilter(PsiKeyword.IMPLEMENTS)))),
          new NotFilter(new LeftNeighbour(new LeftNeighbour(new TextFilter("<", ",")))),
          new LeftNeighbour(new OrFilter(
            new ClassFilter(PsiIdentifier.class),
            new TextFilter(">"))),
          new NotFilter(new ScopeFilter(new InterfaceFilter())));
// completion
      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiClass.class, true);
      variant.addCompletion(PsiKeyword.IMPLEMENTS);
      variant.excludeScopeClass(PsiAnonymousClass.class);

      registerVariant(variant);
    }

// Completion after extends in interface, type parameter and implements in class
// position
    {
      final ElementFilter position = new AndFilter(
        new NotFilter(CLASS_BODY),
        new OrFilter(
            new AndFilter(
                new LeftNeighbour(new TextFilter(PsiKeyword.EXTENDS, ",")),
                new ScopeFilter(new InterfaceFilter())
            ),
            new AndFilter(
                new LeftNeighbour(new TextFilter(PsiKeyword.EXTENDS, "&")),
                new ScopeFilter(new ClassFilter(PsiTypeParameter.class))
            ),
            new LeftNeighbour(new TextFilter(PsiKeyword.IMPLEMENTS, ",")))
      );
// completion
      final OrFilter flags = new OrFilter();
      flags.addFilter(new ThisOrAnyInnerFilter(
        new AndFilter(
            new ClassFilter(PsiClass.class),
            new NotFilter(new AssignableFromContextFilter()),
            new InterfaceFilter())
      ));
      flags.addFilter(new ClassFilter(PsiPackage.class));
      CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiClass.class, true);
      variant.excludeScopeClass(PsiAnonymousClass.class);
      variant.addCompletionFilterOnElement(flags);

      registerVariant(variant);
    }
// Completion for classes in class extends
// position
    {
      final ElementFilter position = new AndFilter(
        new NotFilter(CLASS_BODY),
        new AndFilter(
            new LeftNeighbour(new TextFilter(PsiKeyword.EXTENDS)),
            new ScopeFilter(new NotFilter(new InterfaceFilter())))
      );

// completion
      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiClass.class, true);
      variant.excludeScopeClass(PsiAnonymousClass.class);
      variant.addCompletionFilterOnElement(new ThisOrAnyInnerFilter(
        new AndFilter(
            new ClassFilter(PsiClass.class),
            new NotFilter(new AssignableFromContextFilter()),
            new NotFilter(new InterfaceFilter()),
            new ModifierFilter(PsiModifier.FINAL, false))
      ));
      variant.addCompletionFilterOnElement(new ClassFilter(PsiPackage.class));

      registerVariant(variant);
    }
    {
// declaration start
// position
      final CompletionVariant variant = new CompletionVariant(PsiClass.class, new AndFilter(
        CLASS_BODY,
        new OrFilter(
          END_OF_BLOCK,
          new LeftNeighbour(new OrFilter(
            new TextFilter(MODIFIERS_LIST),
            new SuperParentFilter(new ClassFilter(PsiAnnotation.class)),
            new TokenTypeFilter(JavaTokenType.GT)))
        )));

// completion
      addPrimitiveTypes(variant);
      variant.addCompletion(PsiKeyword.VOID);
      variant.addCompletionFilterOnElement(new ClassFilter(PsiClass.class));
      variant.addCompletionFilterOnElement(new ClassFilter(PsiPackage.class));

      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(new LeftNeighbour(new LeftNeighbour(new TextFilter("<", ","))));
      variant.includeScopeClass(PsiClass.class, true);
      variant.addCompletion(PsiKeyword.EXTENDS, TailType.SPACE);
      registerVariant(variant);
    }
  }

  private void initVariantsInMethodScope() {
    {
// parameters list completion
      final CompletionVariant variant = new CompletionVariant(
        new LeftNeighbour(new OrFilter(new TextFilter(new String[]{"(", ",", PsiKeyword.FINAL}),
                                       new SuperParentFilter(new ClassFilter(PsiAnnotation.class)))));
      variant.includeScopeClass(PsiParameterList.class, true);
      addPrimitiveTypes(variant);
      variant.addCompletion(PsiKeyword.FINAL);
      variant.addCompletionFilterOnElement(new ClassFilter(PsiClass.class));
      registerVariant(variant);
    }

// Completion for classes in method throws section
// position
    {
      final ElementFilter position = new LeftNeighbour(new AndFilter(
        new TextFilter(")"),
        new ParentElementFilter(new ClassFilter(PsiParameterList.class))));

// completion
      CompletionVariant variant = new CompletionVariant(PsiMethod.class, position);
      variant.includeScopeClass(PsiClass.class); // for throws on separate line
      variant.addCompletion(PsiKeyword.THROWS);

      registerVariant(variant);

//in annotation methods
      variant = new CompletionVariant(PsiAnnotationMethod.class, position);
      variant.addCompletion(PsiKeyword.DEFAULT);
      registerVariant(variant);
    }

    {
// Completion for classes in method throws section
// position
      final ElementFilter position = new AndFilter(
        new LeftNeighbour(new TextFilter(PsiKeyword.THROWS, ",")),
        new InsideElementFilter(new ClassFilter(PsiReferenceList.class))
      );

// completion
      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiMethod.class, true);
      variant.addCompletionFilterOnElement(new ThisOrAnyInnerFilter(new AssignableFromFilter("java.lang.Throwable")));
      variant.addCompletionFilterOnElement(new ClassFilter(PsiPackage.class));

      registerVariant(variant);
    }

    {
// completion for declarations
      final CompletionVariant variant = new CompletionVariant(PsiMethod.class, new OrFilter(END_OF_BLOCK, new LeftNeighbour(new TextFilter(PsiKeyword.FINAL))));
      addPrimitiveTypes(variant);
      variant.addCompletion(PsiKeyword.CLASS);
      registerVariant(variant);
    }

// Completion in cast expressions
    {
      final CompletionVariant variant = new CompletionVariant(PsiMethod.class, new LeftNeighbour(new AndFilter(
        new TextFilter("("),
        new ParentElementFilter(new OrFilter(
          new ClassFilter(PsiParenthesizedExpression.class),
          new ClassFilter(PsiTypeCastExpression.class))))));
      addPrimitiveTypes(variant);
      registerVariant(variant);
    }

    {
// instanceof keyword
      final ElementFilter position = new LeftNeighbour(new OrFilter(
          new ReferenceOnFilter(new ClassFilter(PsiVariable.class)),
          new TextFilter(PsiKeyword.THIS),
          new AndFilter(new TextFilter(")"), new ParentElementFilter(new AndFilter(
            new ClassFilter(PsiTypeCastExpression.class, false),
            new OrFilter(
              new ParentElementFilter(new ClassFilter(PsiExpression.class)),
              new ClassFilter(PsiExpression.class))))),
          new AndFilter(new TextFilter("]"), new ParentElementFilter(new ClassFilter(PsiArrayAccessExpression.class)))));
      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiExpression.class, true);
      variant.includeScopeClass(PsiMethod.class);
      variant.addCompletionFilter(new FalseFilter());
      variant.addCompletion(PsiKeyword.INSTANCEOF);

      registerVariant(variant);
    }

    {
// after instanceof keyword
      final ElementFilter position = new PreviousElementFilter(new TextFilter(PsiKeyword.INSTANCEOF));
      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiExpression.class, true);
      variant.addCompletionFilterOnElement(new ClassFilter(PsiClass.class));

      registerVariant(variant);
    }

    {
// after final keyword
      final ElementFilter position = new AndFilter(new SuperParentFilter(new ClassFilter(PsiCodeBlock.class)),
                                                   new LeftNeighbour(new TextFilter(PsiKeyword.FINAL)));
      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiDeclarationStatement.class, true);
      variant.addCompletionFilterOnElement(new ClassFilter(PsiClass.class));
      addPrimitiveTypes(variant);

      registerVariant(variant);
    }

    {
// Keyword completion in start of declaration
      final CompletionVariant variant = new CompletionVariant(PsiMethod.class, END_OF_BLOCK);
      variant.addCompletion(PsiKeyword.THIS, TailType.NONE);
      variant.addCompletion(PsiKeyword.SUPER, TailType.NONE);
      addKeywords(variant);
      registerVariant(variant);
    }

    {
// Keyword completion in returns
      final CompletionVariant variant = new CompletionVariant(PsiMethod.class, new LeftNeighbour(new TextFilter(PsiKeyword.RETURN)));
      variant.addCompletion(PsiKeyword.THIS, TailType.NONE);
      variant.addCompletion(PsiKeyword.SUPER, TailType.NONE);
      registerVariant(variant);
    }


// Catch/Finally completion
    {
      final ElementFilter position = new LeftNeighbour(new AndFilter(
        new TextFilter("}"),
        new ParentElementFilter(new AndFilter(
          new LeftNeighbour(new TextFilter(PsiKeyword.TRY)),
          new ParentElementFilter(new ClassFilter(PsiTryStatement.class))))));

      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiCodeBlock.class, true);
      variant.addCompletion(PsiKeyword.CATCH, TailType.LPARENTH);
      variant.addCompletion(PsiKeyword.FINALLY, '{');
      variant.addCompletionFilter(new FalseFilter());
      registerVariant(variant);
    }

// Catch/Finnaly completion
    {
      final ElementFilter position = new LeftNeighbour(new AndFilter(
        new TextFilter("}"),
        new ParentElementFilter(new AndFilter(
          new LeftNeighbour(new NotFilter(new TextFilter(PsiKeyword.TRY))),
          new OrFilter(
            new ParentElementFilter(new ClassFilter(PsiTryStatement.class)),
            new ParentElementFilter(new ClassFilter(PsiCatchSection.class)))
          ))));

      final CompletionVariant variant = new CompletionVariant(position);
      variant.includeScopeClass(PsiCodeBlock.class, false);
      variant.addCompletion(PsiKeyword.CATCH, TailType.LPARENTH);
      variant.addCompletion(PsiKeyword.FINALLY, '{');
      //variant.addCompletionFilter(new FalseFilter());
      registerVariant(variant);
    }

    {
// Completion for catches
      final CompletionVariant variant = new CompletionVariant(PsiTryStatement.class, new PreviousElementFilter(new AndFilter(
        new ParentElementFilter(new ClassFilter(PsiTryStatement.class)),
        new TextFilter("(")
      )));
      variant.includeScopeClass(PsiParameter.class);

      variant.addCompletionFilterOnElement(new ThisOrAnyInnerFilter(new AssignableFromFilter("java.lang.Throwable")));
      variant.addCompletionFilterOnElement(new ClassFilter(PsiPackage.class));

      registerVariant(variant);
    }

// Completion for else expression
// completion
    {
      final ElementFilter position = new LeftNeighbour(
        new OrFilter(
          new AndFilter(new TextFilter("}"),new ParentElementFilter(new ClassFilter(PsiIfStatement.class), 3)),
          new AndFilter(new TextFilter(";"),new ParentElementFilter(new ClassFilter(PsiIfStatement.class), 2))
        ));
      final CompletionVariant variant = new CompletionVariant(PsiMethod.class, position);
      variant.addCompletion(PsiKeyword.ELSE);

      registerVariant(variant);
    }

    {
// Super/This keyword completion
      final ElementFilter position =
        new LeftNeighbour(
          new AndFilter(
            new TextFilter("."),
            new LeftNeighbour(
              new ReferenceOnFilter(new GeneratorFilter(EqualsFilter.class, new UpWalkGetter(new ClassFilter(PsiClass.class))))
            )));
      final CompletionVariant variant = new CompletionVariant(PsiMethod.class, position);
      variant.includeScopeClass(PsiVariable.class);
      variant.addCompletion(PsiKeyword.SUPER, TailType.DOT);
      variant.addCompletion(PsiKeyword.THIS, TailType.DOT);
      registerVariant(variant);
    }
    {
// Class field completion
      final CompletionVariant variant = new CompletionVariant(PsiMethod.class, new LeftNeighbour(
        new AndFilter(
            new TextFilter("."),
            new LeftNeighbour(
                new OrFilter(
                    new ReferenceOnFilter(new ClassFilter(PsiClass.class)),
                    new TextFilter(PRIMITIVE_TYPES),
                    new TextFilter("]"))))));

      variant.includeScopeClass(PsiVariable.class);
      variant.addCompletion(PsiKeyword.CLASS, TailType.NONE);
      registerVariant(variant);
    }

    {
// break completion
      final CompletionVariant variant = new CompletionVariant(new AndFilter(END_OF_BLOCK, new OrFilter(
        new ScopeFilter(new ClassFilter(PsiSwitchStatement.class)),
        new InsideElementFilter(new ClassFilter(PsiBlockStatement.class)))));

      variant.includeScopeClass(PsiForStatement.class, false);
      variant.includeScopeClass(PsiForeachStatement.class, false);
      variant.includeScopeClass(PsiWhileStatement.class, false);
      variant.includeScopeClass(PsiDoWhileStatement.class, false);
      variant.includeScopeClass(PsiSwitchStatement.class, false);
      variant.addCompletion(PsiKeyword.BREAK);
      registerVariant(variant);
    }
    {
// continue completion
      final CompletionVariant variant = new CompletionVariant(new AndFilter(END_OF_BLOCK, new InsideElementFilter(new ClassFilter(PsiBlockStatement.class))));
      variant.includeScopeClass(PsiForeachStatement.class, false);
      variant.includeScopeClass(PsiForStatement.class, false);
      variant.includeScopeClass(PsiWhileStatement.class, false);
      variant.includeScopeClass(PsiDoWhileStatement.class, false);

      variant.addCompletion(PsiKeyword.CONTINUE);
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(
        new AndFilter(
          END_OF_BLOCK,
          new OrFilter(
            new ParentElementFilter(new ClassFilter(PsiSwitchLabelStatement.class)),
            new LeftNeighbour(new OrFilter(
              new ParentElementFilter(new ClassFilter(PsiSwitchStatement.class), 2),
              new AndFilter(new TextFilter(";", "}"),new ParentElementFilter(new ClassFilter(PsiSwitchStatement.class), 3)
              ))))));
      variant.includeScopeClass(PsiSwitchStatement.class, true);
      variant.addCompletion(PsiKeyword.CASE, TailType.SPACE);
      variant.addCompletion(PsiKeyword.DEFAULT, ':');
      registerVariant(variant);
    }

    {
// primitive arrays after new
      final CompletionVariant variant = new CompletionVariant(PsiExpression.class, new LeftNeighbour(
        new AndFilter(new TextFilter(PsiKeyword.NEW), new LeftNeighbour(new NotFilter(new TextFilter(".", PsiKeyword.THROW)))))
      );
      variant.includeScopeClass(PsiNewExpression.class, true);
      addPrimitiveTypes(variant);
      variant.setItemProperty(LookupItem.BRACKETS_COUNT_ATTR, 1);
      registerVariant(variant);
    }

    {
// after new
      final CompletionVariant variant = new CompletionVariant(new LeftNeighbour(new TextFilter(PsiKeyword.NEW)));
      variant.includeScopeClass(PsiNewExpression.class, true);
      variant.addCompletionFilterOnElement(new ClassFilter(PsiClass.class));

      registerVariant(variant);
    }


    {
      final CompletionVariant variant = new CompletionVariant(new AndFilter(
        new ScopeFilter(new ParentElementFilter(new ClassFilter(PsiThrowStatement.class))),
        new ParentElementFilter(new ClassFilter(PsiNewExpression.class)))
      );
      variant.includeScopeClass(PsiNewExpression.class, false);
      variant.addCompletionFilterOnElement(new ThisOrAnyInnerFilter(new AssignableFromFilter("java.lang.Throwable")));

      registerVariant(variant);
    }

    {
// completion in reference parameters
      final CompletionVariant variant = new CompletionVariant(TrueFilter.INSTANCE);
      variant.includeScopeClass(PsiReferenceParameterList.class, true);
      variant.addCompletionFilterOnElement(new ClassFilter(PsiClass.class));

      registerVariant(variant);
    }

    {
      // null completion
      final CompletionVariant variant = new CompletionVariant(new NotFilter(new LeftNeighbour(new TextFilter("."))));
      variant.addCompletion(PsiKeyword.NULL,TailType.NONE);
      variant.includeScopeClass(PsiExpressionList.class);
      registerVariant(variant);
    }
  }

  private void initVariantsInFieldScope() {
    {
// completion in initializer
      final CompletionVariant variant = new CompletionVariant(new AfterElementFilter(new TextFilter("=")));
      variant.includeScopeClass(PsiVariable.class, false);
      variant.addCompletionFilterOnElement(new OrFilter(
        new ClassFilter(PsiVariable.class, false),
        new ExcludeDeclaredFilter(new ClassFilter(PsiVariable.class))
      ));
      registerVariant(variant);
    }
  }

  private static void addPrimitiveTypes(CompletionVariant variant){
    addPrimitiveTypes(variant, CompletionVariant.DEFAULT_TAIL_TYPE);
  }

  private static void addPrimitiveTypes(CompletionVariant variant, int tailType){
    variant.addCompletion(new String[]{
      PsiKeyword.SHORT, PsiKeyword.BOOLEAN,
      PsiKeyword.DOUBLE, PsiKeyword.LONG,
      PsiKeyword.INT, PsiKeyword.FLOAT, PsiKeyword.CHAR
    }, tailType);
  }

  private static void addKeywords(CompletionVariant variant){
    variant.addCompletion(PsiKeyword.SWITCH, TailType.LPARENTH);
    variant.addCompletion(PsiKeyword.WHILE, TailType.LPARENTH);
    variant.addCompletion(PsiKeyword.FOR, TailType.LPARENTH);
    variant.addCompletion(PsiKeyword.TRY, '{');
    variant.addCompletion(PsiKeyword.THROW, TailType.SPACE);
    variant.addCompletion(PsiKeyword.RETURN, TailType.SPACE);
    variant.addCompletion(PsiKeyword.NEW, TailType.SPACE);
    variant.addCompletion(PsiKeyword.ASSERT, TailType.SPACE);
  }

  static final AndFilter START_OF_CODE_FRAGMENT = new AndFilter(
    new ScopeFilter(new AndFilter(
      new ClassFilter(PsiCodeFragment.class),
      new ClassFilter(PsiExpressionCodeFragment.class, false)
    )),
    new StartElementFilter()
  );

  static final ElementFilter END_OF_BLOCK = new OrFilter(
    new AndFilter(
      new LeftNeighbour(
          new OrFilter(
              new TextFilter(ourBlockFinalizers),
              new TextFilter("*/"),
              new TokenTypeFilter(JspElementType.HOLDER_TEMPLATE_DATA),
              new AndFilter(
                  new TextFilter(")"),
                  new NotFilter(
                      new OrFilter(
                          new ParentElementFilter(new ClassFilter(PsiExpressionList.class)),
                          new ParentElementFilter(new ClassFilter(PsiParameterList.class))
                      )
                  )
              ))),
      new NotFilter(new TextFilter("."))
    ),
    START_OF_CODE_FRAGMENT
  );

  private static final String[] PRIMITIVE_TYPES = new String[]{
    PsiKeyword.SHORT, PsiKeyword.BOOLEAN,
    PsiKeyword.DOUBLE, PsiKeyword.LONG,
    PsiKeyword.INT, PsiKeyword.FLOAT,
    PsiKeyword.VOID, PsiKeyword.CHAR, PsiKeyword.BYTE
  };

  private final static ElementFilter CLASS_BODY = new OrFilter(
    new AfterElementFilter(new TextFilter("{")),
    new ScopeFilter(new ClassFilter(JspClass.class)));
}
