// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.NotNull;

import static com.intellij.structuralsearch.PredefinedConfigurationUtil.*;

/**
 * @author Bas Leijdekkers
 */
final class JavaPredefinedConfigurations {
  @NotNull
  public static Configuration @NotNull [] createPredefinedTemplates() {
    return new Configuration[]{
      // Expression patterns
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.method.calls"), "method calls",
                                "'_Instance?.'MethodCall('_Parameter*)",
                                getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.new.expressions"), "new expressions",
                                "new 'Constructor('_Argument*)", getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.lambdas"), "lambdas",
                                "('_Parameter*) -> {}", getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.field.selections"), "field selections",
                                "'_Instance?.'Field", getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.array.access"), "array access",
                                "'_Array['_Index]", getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.assignments"), "assignments",
                                "'_Inst = '_Expr", getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.casts"), "casts",
                                "('_Type)'_Expr", getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.instanceof"), "instanceof",
                                "'_Expr instanceof '_Type", getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.string.literals"), "string literals",
                                "\"'_String\"", getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.all.expressions.of.some.type"), "all expressions of some type",
                                "'_Expression:[exprtype( SomeType )]",
                                getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.sample.method.invokation.with.constant.argument"),
                                "sample method invocation with constant argument",
                                "Integer.parseInt('_a:[script( \"com.intellij.psi.util.PsiUtil.isConstantExpression(__context__)\" )])",
                                getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.method.references"), "method references",
                                "'_Qualifier::'Method",
                                getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.string.concatenations"),
                                "string concatenations with many operands",
                                "[exprtype( java\\.lang\\.String )]'_a + '_b{10,}",
                                getExpressionType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.deprecated.method.calls"), "method calls to deprecated methods",
                                "'_Instance?.'MethodCall:[ref( deprecated methods )]('_Parameter*)",
                                getExpressionType(), JavaFileType.INSTANCE),
      createConfiguration(SSRBundle.message("predefined.configuration.pattern.matching.instanceof"), "Pattern matching instanceof",
                          "$operand$ instanceof $Type$ $var$",
                          getExpressionType(), JavaFileType.INSTANCE),

      // Operators
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.block.dcls"), "block dcls",
                                "{\n  '_Type 'Var+ = '_Init?;\n  '_BlockStatements*;\n}",
                                getOperatorType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.trys"), "try's",
                                "try {\n  '_TryStatement+;\n} catch('_ExceptionType '_Exception) {\n  '_CatchStatement*;\n}",
                                getOperatorType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.ifs"), "if's",
                                "if ('_Condition) {\n  '_ThenStatement*;\n} else {\n  '_ElseStatement*;\n}",
                                getOperatorType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.switches"), "switches",
                                "switch('_Condition) {\n  '_Statement*;\n}",
                                getOperatorType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.foreaches"), "foreach loops",
                                "for ('_Type '_Variable : '_Expression) {\n  '_Statement*;\n}",
                                getOperatorType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.logging.without.if"), "logging without if",
                                "[!within( statement in if )]LOG.debug('_Argument*);",
                                getOperatorType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.statement.in.if"), "statement in if",
                                "if('_condition) { 'statement*; }",
                                getOperatorType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.assert.without.description"),
                                "assert statement without description",
                                "assert '_condition : '_description{0};",
                                getOperatorType(), JavaFileType.INSTANCE),

      // Class based
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.methods.of.the.class"), "constructors \\& methods",
                                "'_ReturnType? '_Method('_ParameterType '_Parameter*);",
                                getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.deprecated.methods"), "deprecated methods",
                                "@Deprecated\n'_ReturnType '_Method('_ParameterType '_Parameter*);",
                                getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.fields.of.the.class"), "fields of a class",
                                """
                                  class '_Class:[script( "!__context__.interface && !__context__.enum && !__context__.record" )] {
                                    '_FieldType 'Field = '_Init?;
                                  }""",
                                getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.all.methods.of.the.class.within.hierarchy"),
                                "all methods of a class (within hierarchy)",
                                """
                                  class '_Class:[script( "!__context__.interface && !__context__.enum && !__context__.record" )] {
                                    '_ReturnType 'Method:* ('_ParameterType '_Parameter*);
                                  }""",
                                getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.all.fields.of.the.class"), "all fields of a class",
                                """
                                  class '_Class:[script( "!__context__.interface && !__context__.enum && !__context__.record" )] {
                                    '_FieldType 'Field:* = '_Init?;
                                  }""",
                                getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.instance.fields.of.the.class"), "instance fields of a class",
                                "class '_Class { \n  @Modifier(\"Instance\") '_FieldType 'Field = '_Init?;\n}",
                                getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.packagelocal.fields.of.the.class"), "package-private fields",
                                "@Modifier(\"packageLocal\") '_FieldType 'Field = '_Init?;",
                                getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.constructors.of.the.class"), "class constructors",
                                "'Class('_ParameterType '_Parameter*) {\n  '_Statement*;\n}",
                                getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.classes"), "classes",
                                "class 'Class:[script( \"!__context__.interface && !__context__.enum && !__context__.record\" )] {}",
                                getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.classes.interfaces.enums"), "classes, interfaces \\& enums",
                                "class 'ClassInterfaceEnum {}",
                                getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.direct.subclasses"), "direct subclasses",
                                "class 'Class extends '_Parent {}",
                                getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.implementors.of.interface.within.hierarchy"),
                                "implementors of interface (within hierarchy)",
                                "class 'Class implements '_Interface:* {}",
                                getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.interfaces"), "interfaces",
                                "interface 'Interface {}",
                                getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.inner.classes"), "inner classes",
                                "class '_ {\n  class 'InnerClass {}\n}",
                                getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.all.inner.classes.within.hierarchy"),
                                "all inner classes (within hierarchy)",
                                "class '_Class {\n  class 'InnerClass:* {}\n}",
                                getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.anonymous.classes"), "anonymous classes",
                                "new 'AnonymousClass() {}",
                                getClassType(), JavaFileType.INSTANCE),
      createConfiguration(SSRBundle.message("predefined.configuration.local.classes"), "Local classes",
                          """
                            {
                              '_before*;
                              class 'Local {}
                              '_after*;
                            }""",
                          getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.class.implements.two.interfaces"),
                                "class implementing two interfaces",
                                "class 'A implements '_Interface1:[regex( *java\\.lang\\.Cloneable )], '_Interface2:*java\\.io\\.Serializable {\n}",
                                getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.class.static.blocks"), "static initializers",
                                "static {\n  '_Statement*;\n}",
                                getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.class.instance.initialization.blocks"), "instance initializers",
                                "@Modifier(\"Instance\") {\n  '_Statement*;\n}",
                                getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.class.any.initialization.blocks"), "any initializer",
                                "{\n  '_Statement*;\n}",
                                getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.enums"), "enums",
                                "enum 'Enum {}",
                                getClassType(), JavaFileType.INSTANCE),
      createConfiguration(SSRBundle.message("predefined.configuration.records"), "Records",
                          "record 'Record('_Type '_component*) {}",
                          getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.class.with.parameterless.constructors"),
                                "classes with parameterless constructors",
                                "class 'Class {\n  '_Method{0,0}('_ParameterType '_Parameter+);\n}",
                                getClassType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.static.fields.without.final"),
                                "static fields that are not final",
                                "static '_Type 'Variable:[ script( \"!__context__.hasModifierProperty(\"final\")\" ) ] = '_Init?;",
                                getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.interfaces.having.no.descendants"),
                                "interface that is not implemented or extended",
                                "interface 'A:[script( \"com.intellij.psi.search.searches.ClassInheritorsSearch.search(__context__).findFirst() == null\" )] {}",
                                getClassType(), JavaFileType.INSTANCE),

      // Generics
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.generic.classes"), "generic classes",
                                "class 'GenericClass<'_TypeParameter+> {} ",
                                getGenericsType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.generic.methods"), "generic methods",
                                "<'_TypeParameter+> '_Type '_Method('_ParameterType '_Parameter*);",
                                getGenericsType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.typed.symbol"), "typed symbol",
                                "'Symbol <'_GenericArgument+>",
                                getGenericsType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.generic.casts"), "generic casts",
                                "( '_Type <'_GenericArgument+> ) '_Expr",
                                getGenericsType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.type.var.substitutions.in.instanceof.with.generic.types"),
                                "type var substitutions in instanceof with generic types",
                                "'_Expr instanceof '_Type <'Substitutions> ",
                                getGenericsType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.variables.of.generic.types"), "variables of generic types",
                                "'_Type <'_GenericArgument+>  'Var = '_Init?;",
                                getGenericsType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.diamond.operators"), "diamond operators",
                                "new 'Class<>('_Argument*)",
                                getGenericsType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.method.returns.bounded.wildcard"),
                                "method returns bounded wildcard",
                                "[script( \"!Method.hasModifierProperty(com.intellij.psi.PsiModifier.ABSTRACT)\" )]'_Type<? extends '_Bound> 'Method('_ParameterType '_Parameter*);",
                                getGenericsType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.generic.constructors"), "generic constructors",
                                "<'_TypeParameter+> 'Class('_ParameterType '_Parameter*) {\n  '_Statement*;\n}",
                                getGenericsType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),

      // Add comments and metadata
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.comments"), "comments",
                                "/* 'CommentContent */", getMetadataType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.javadoc.annotated.class"), "javadoc annotated class",
                                """
                                  /**
                                   * '_Comment
                                   * @'_Tag* '_TagValue*
                                   */
                                  class '_Class {
                                  }""", getMetadataType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.javadoc.annotated.methods"),
                                "javadoc annotated methods \\& constructors",
                                """
                                  /**
                                   * '_Comment
                                   * @'_Tag* '_TagValue*
                                   */
                                  '_Type? '_Method('_ParameterType '_Parameter*);""",
                                getMetadataType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.javadoc.annotated.fields"), "javadoc annotated fields",
                                """
                                  /**
                                   * '_Comment
                                   * @'_Tag* '_TagValue*
                                   */
                                  '_Type+ 'Field = '_Init*;""",
                                getMetadataType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.javadoc.tags"), "javadoc tags",
                                """
                                  /**
                                   * '_Comment
                                   * @'Tag '_TagValue*
                                   */""",
                                getMetadataType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.xdoclet.metadata"), "XDoclet metadata",
                                "/** @'Tag \n  '_Property+\n*/",
                                getMetadataType(), JavaFileType.INSTANCE),

      createLegacyConfiguration(SSRBundle.message("predefined.configuration.annotations"), "annotations",
                                "@'_Annotation", getMetadataType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.annotated.class"), "annotated classes",
                                "@'_Annotation\n" +
                                "class 'Class {}", getMetadataType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.annotated.fields"), "annotated fields",
                                """
                                  @'_Annotation+
                                  '_FieldType 'Field = '_Init?;
                                  """,
                                getMetadataType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.annotated.methods"), "annotated methods",
                                "@'_Annotation+\n'_MethodType '_Method('_ParameterType '_Parameter*);",
                                getMetadataType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),

      createLegacyConfiguration(SSRBundle.message("predefined.configuration.not.annotated.methods"), "not annotated methods",
                                "@'_Annotation{0,0}\n'_MethodType '_Method('_ParameterType '_Parameter*);",
                                getMetadataType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),

      createLegacyConfiguration(SSRBundle.message("predefined.configuration.annotation.declarations"), "annotation declarations",
                                "@interface 'Interface {}", getMetadataType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.comments.containing.word"), "comments containing a given word",
                                "// '_before bug '_after",
                                getMetadataType(), JavaFileType.INSTANCE),

      // J2EE templates
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.struts.1.1.actions"), "Struts 1.1 actions",
                                """
                                  public class '_StrutsActionClass extends '_ParentClass*:Action {
                                    public ActionForward '_AnActionMethod:*execute (ActionMapping '_action,
                                                                   ActionForm '_form,
                                                                   HttpServletRequest '_request,
                                                                   HttpServletResponse '_response);
                                  }""", getJ2EEType(), JavaFileType.INSTANCE, null),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.entity.ejb"), "entity ejb",
                                """
                                  class 'EntityBean implements EntityBean {
                                    EntityContext '_Context?;

                                    public void setEntityContext(EntityContext '_Context2);

                                    public '_RetType ejbCreate('_CreateType '_CreateDcl*);
                                    public void ejbActivate();

                                    public void ejbLoad();

                                    public void ejbPassivate();

                                    public void ejbRemove();

                                    public void ejbStore();
                                  }""", getJ2EEType(), JavaFileType.INSTANCE, null),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.session.ejb"), "session ejb",
                                """
                                  class 'SessionBean implements SessionBean {
                                    SessionContext '_Context?;

                                    public void '_setSessionContext(SessionContext '_Context2);

                                    public '_RetType ejbCreate('_CreateParameterType '_CreateParameterDcl*);
                                    public void ejbActivate();

                                    public void ejbPassivate();

                                    public void ejbRemove();
                                  }""", getJ2EEType(), JavaFileType.INSTANCE, null),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.ejb.interface"), "ejb interface",
                                """
                                  interface 'EjbInterface extends EJBObject {
                                    '_Type '_Method+('_ParameterType '_Param*);
                                  }""", getJ2EEType(), JavaFileType.INSTANCE, null),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.servlets"), "servlets",
                                """
                                  public class 'Servlet extends '_ParentClass:*HttpServlet {
                                    public void '_InitServletMethod?:init ();
                                    public void '_DestroyServletMethod?:destroy ();
                                    void '_ServiceMethod?:*service (HttpServletRequest '_request, HttpServletResponse '_response);
                                    void '_SpecificServiceMethod*:do.* (HttpServletRequest '_request2, HttpServletResponse '_response2);\s
                                  }""", getJ2EEType(), JavaFileType.INSTANCE, null),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.filters"), "filters",
                                """
                                  public class 'Filter implements Filter {
                                    public void '_DestroyFilterMethod?:*destroy ();
                                    public void '_InitFilterMethod?:*init ();
                                    public void '_FilteringMethod:*doFilter (ServletRequest '_request,
                                      ServletResponse '_response,FilterChain '_chain);
                                  }""", getJ2EEType(), JavaFileType.INSTANCE, null),

      // Misc types
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.serializable.classes.and.their.serialization.implementation"),
                                "Serializable classes and their serialization implementation",
                                """
                                  class 'Class implements '_Serializable:*Serializable {
                                    static final long '_VersionField?:serialVersionUID = '_VersionFieldInit?;
                                    private static final ObjectStreamField[] '_persistentFields?:serialPersistentFields = '_persistentFieldInitial?;\s
                                    private void '_SerializationWriteHandler?:writeObject (ObjectOutputStream '_stream) throws IOException;
                                    private void '_SerializationReadHandler?:readObject (ObjectInputStream '_stream2) throws IOException, ClassNotFoundException;
                                    Object '_SpecialSerializationReadHandler?:readResolve () throws ObjectStreamException;
                                    Object '_SpecialSerializationWriteHandler?:writeReplace () throws ObjectStreamException;
                                  }""", getMiscType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.cloneable.implementations"), "Cloneable implementations",
                                """
                                  class '_Class implements '_Interface:*Cloneable {
                                    Object 'CloningMethod:*clone ();
                                  }""", getMiscType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.junit.test.cases"), "junit test cases",
                                """
                                  public class 'TestCase extends '_TestCaseClazz:*TestCase {
                                    public void '_testMethod+:test.* ();
                                  }""", getMiscType(), JavaFileType.INSTANCE, null),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.singletons"), "singletons",
                                """
                                  class 'Class {
                                    private 'Class('_ParameterType '_Parameter*) {
                                     '_ConstructorStatement*;
                                    }
                                    private static '_Class '_Instance;
                                    static '_Class '_GetInstance() {
                                      '_SomeStatement*;
                                      return '_Instance;
                                    }
                                  }""", getMiscType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.similar.methods.structure"), "similar methods structure",
                                """
                                  '_RetType '_Method('_ParameterType '_Parameter*) throws 'ExceptionType {
                                    try {
                                      '_OtherStatements+;
                                    } catch('_SomeException '_Exception) {
                                      '_CatchStatement*;
                                      throw new 'ExceptionType('_ExceptionConstructorArgs*);
                                    }
                                  }""", getMiscType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.bean.info.classes"), "Bean info classes",
                                "class 'A implements '_:*java\\.beans\\.BeanInfo {\n" +
                                "}", getMiscType(), JavaFileType.INSTANCE),

      // interesting types
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.symbol"), "symbol",
                                "'Symbol", getInterestingType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.fields.variables.read"), "fields/variables read",
                                """
                                  'Symbol:[ script( "import com.intellij.psi.*
                                  import static com.intellij.psi.util.PsiUtil.*
                                  Symbol instanceof PsiReferenceExpression && isAccessedForReading(Symbol)" ) ]""",
                                getInterestingType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.fields_variables.with.given.name.pattern.updated"),
                                "fields/variables with given name pattern updated",
                                """
                                  'Symbol:[regex( name ) && script( "import com.intellij.psi.*
                                  import static com.intellij.psi.util.PsiUtil.*
                                  Symbol instanceof PsiExpression && isAccessedForWriting(Symbol) ||
                                    Symbol instanceof PsiVariable && Symbol.getInitializer() != null" )]""", getInterestingType(),
                                JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.usage.of.derived.type.in.cast"),
                                "usage of derived type in cast",
                                "('CastType:*[regex( Base )]) '_Expr",
                                getInterestingType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.boxing.in.declarations"), "boxing in declarations",
                                "'_Type:Object|Integer|Boolean|Long|Character|Short|Byte 'Var = '_Value:[exprtype( int|boolean|long|char|short|byte )];",
                                getInterestingType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.unboxing.in.declarations"), "unboxing in declarations",
                                "'_Type:int|boolean|long|char|short|byte 'Var = '_Value:[exprtype( Integer|Boolean|Long|Character|Short|Byte )];",
                                getInterestingType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.boxing.in.method.calls"), "boxing in method calls",
                                "'_Instance?.'Call('_BeforeParam*,'_Param:[ exprtype( int|boolean|long|char|short|byte ) && formal( Object|Integer|Boolean|Long|Character|Short|Byte ) ],'_AfterParam*)",
                                getInterestingType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.unboxing.in.method.calls"), "unboxing in method calls",
                                "'_Instance?.'Call('_BeforeParam*,'_Param:[ formal( int|boolean|long|char|short|byte ) && exprtype( Integer|Boolean|Long|Character|Short|Byte ) ],'_AfterParam*)",
                                getInterestingType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.any.boxing"), "boxed expressions",
                                "'_expression:[ exprtype( int|boolean|long|char|short|byte ) && formal( Object|Integer|Boolean|Long|Character|Short|Byte ) ]",
                                getInterestingType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.any.unboxing"), "unboxed expressions",
                                "'_expression:[ formal( int|boolean|long|char|short|byte ) && exprtype( Integer|Boolean|Long|Character|Short|Byte ) ]",
                                getInterestingType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.try.without.resources"),
                                "try statements without resources and catch blocks",
                                "try ('_ResourceType '_resource{0,0} = '_init; '_expression{0,0}) {\n  '_TryStatement*;\n} catch('_ExceptionType '_Exception{0,0}) {\n  '_CatchStatement*;\n}",
                                getInterestingType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.switch.with.branches"),
                                "switch statements \\& expressions with few branches",
                                """
                                  [ script( "import com.intellij.psi.*;
                                  import com.intellij.psi.util.*;
                                  PsiTreeUtil.getChildrenOfType(__context__.body, PsiSwitchLabelStatementBase.class).length < 5" ) ]switch ('_expression) {
                                  }""",
                                getInterestingType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.labeled.break"), "labeled break statements",
                                "break '_label;", getInterestingType(), JavaFileType.INSTANCE),
      createLegacyConfiguration(SSRBundle.message("predefined.configuration.methods.with.final.parameters"),
                                "methods \\& constructors with final parameters",
                                "'_ReturnType? '_Method('_BeforeType '_BeforeParameter*, final '_ParameterType '_Parameter, '_AfterType '_AfterParameter*);",
                                getInterestingType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createConfiguration(SSRBundle.message("predefined.configuration.double.checked.locking"), "double-checked locking",
                          """
                            if ('_condition) {
                              synchronized ('_lock) {
                                if ('_condition) {
                                  '_statement+;
                                }
                              }
                            }""",
                          getInterestingType(), JavaFileType.INSTANCE),
      //createSearchTemplateInfo("methods called","'_?.'_:[ref('Method)] ('_*)", INTERESTING_TYPE),
      //createSearchTemplateInfo("fields selected","'_?.'_:[ref('Field)] ", INTERESTING_TYPE),
      //createSearchTemplateInfo("symbols used","'_:[ref('Symbol)] ", INTERESTING_TYPE),
      //createSearchTemplateInfo("types used","'_:[ref('Type)] '_;", INTERESTING_TYPE),
    };
  }

  private static String getExpressionType() {
    return SSRBundle.message("expressions.category");
  }

  private static String getInterestingType() {
    return SSRBundle.message("interesting.category");
  }

  private static String getJ2EEType() {
    return SSRBundle.message("j2ee.category");
  }

  private static String getOperatorType() {
    return SSRBundle.message("operators.category");
  }

  private static String getClassType() {
    return SSRBundle.message("class.category");
  }

  private static String getMetadataType() {
    return SSRBundle.message("metadata.category");
  }

  private static String getMiscType() {
    return SSRBundle.message("misc.category");
  }

  private static String getGenericsType() {
    return SSRBundle.message("generics.category");
  }
}
