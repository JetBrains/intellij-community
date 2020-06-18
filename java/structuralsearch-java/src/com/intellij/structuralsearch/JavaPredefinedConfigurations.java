// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.plugin.ui.Configuration;

import static com.intellij.structuralsearch.PredefinedConfigurationUtil.createSearchTemplateInfo;
import static com.intellij.structuralsearch.PredefinedConfigurationUtil.createSearchTemplateInfoSimple;

/**
* @author Bas Leijdekkers
*/
class JavaPredefinedConfigurations {
  public static Configuration[] createPredefinedTemplates() {
    return new Configuration[] {
      // Expression patterns
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.method.calls"), "'_Instance?.'MethodCall('_Parameter*)",
                               getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.new.expressions"), "new 'Constructor('_Argument*)",
                               getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.lambdas"), "('_Parameter*) -> {}", getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.field.selections"), "'_Instance?.'Field", getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.array.access"), "'_Array['_Index]", getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.assignments"), "'_Inst = '_Expr", getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.casts"), "('_Type)'_Expr", getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.instanceof"), "'_Expr instanceof '_Type", getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.string.literals"), "\"'_String\"", getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.all.expressions.of.some.type"), "'_Expression:[exprtype( SomeType )]",
                               getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.sample.method.invokation.with.constant.argument"), "Integer.parseInt('_a:[script( \"com.intellij.psi.util.PsiUtil.isConstantExpression(__context__)\" )])",
                               getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.method.references"), "'_Qualifier::'Method",
                               getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.string.concatenations"), "[exprtype( java\\.lang\\.String )]'_a + '_b{10,}",
                               getExpressionType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.deprecated.method.calls"), "'_Instance?.'MethodCall:[ref( deprecated methods )]('_Parameter*)",
                               getExpressionType()),

      // Operators
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.block.dcls"), "{\n  '_Type 'Var+ = '_Init?;\n  '_BlockStatements*;\n}",
                               getOperatorType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.trys"), "try {\n  '_TryStatement+;\n} catch('_ExceptionType '_Exception) {\n  '_CatchStatement*;\n}",
                               getOperatorType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.ifs"), "if ('_Condition) {\n  '_ThenStatement*;\n} else {\n  '_ElseStatement*;\n}",
                               getOperatorType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.switches"), "switch('_Condition) {\n  '_Statement*;\n}",
                               getOperatorType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.foreaches"), "for ('_Type '_Variable : '_Expression) {\n  '_Statement*;\n}",
                               getOperatorType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.logging.without.if"), "[!within( statement in if )]LOG.debug('_Argument*);",
                               getOperatorType()),
      createSearchTemplateInfo("statement in if", "if('_condition) { 'statement*; }", getOperatorType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.assert.without.description"), "assert '_condition : '_description{0};",
                               getOperatorType()),

      // Class based
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.methods.of.the.class"),
        "'_ReturnType? '_Method('_ParameterType '_Parameter*);",
        getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.deprecated.methods"),
        "@Deprecated\n'_ReturnType '_Method('_ParameterType '_Parameter*);",
        getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.fields.of.the.class"),
        "class '_Class { \n  '_FieldType 'Field+ = '_Init?;\n}",
        getClassType()
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.all.methods.of.the.class.within.hierarchy"),
        "class '_ { \n  '_ReturnType 'Method+:* ('_ParameterType '_Parameter*);\n}",
        getClassType()
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.all.fields.of.the.class"),
        "class '_Class { \n  '_FieldType 'Field+:* = '_Init?;\n}",
        getClassType()
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.instance.fields.of.the.class"),
        "class '_Class { \n  @Modifier(\"Instance\") '_FieldType 'Field+ = '_Init?;\n}",
        getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.packagelocal.fields.of.the.class"),
        "@Modifier(\"packageLocal\") '_FieldType 'Field = '_Init?;",
        getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.constructors.of.the.class"),
        "'Class('_ParameterType '_Parameter*) {\n  '_Statement*;\n}",
        getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.classes"),
        "class 'Class:[script( \"!__context__.interface && !__context__.enum\" )] {}",
        getClassType()
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.classes.interfaces.enums"),
        "class 'ClassInterfaceEnum {}",
        getClassType()
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.direct.subclasses"),
        "class 'Class extends '_Parent {}",
        getClassType()
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.implementors.of.interface.within.hierarchy"),
        "class 'Class implements '_Interface:* {}",
        getClassType()
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.interfaces"),
        "interface 'Interface {}",
        getClassType()
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.inner.classes"),
        "class '_ {\n  class 'InnerClass+ {}\n}",
        getClassType()
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.all.inner.classes.within.hierarchy"),
        "class '_Class {\n  class 'InnerClass+:* {}\n}",
        getClassType()
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.anonymous.classes"),
        "new 'AnonymousClass() {}",
        getClassType()
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.class.implements.two.interfaces"),
        "class 'A implements '_Interface1:[regex( *java\\.lang\\.Cloneable )], '_Interface2:*java\\.io\\.Serializable {\n" +"}",
        getClassType()
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.class.static.blocks"),
        "static {\n  '_Statement*;\n}",
        getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.class.instance.initialization.blocks"),
        "@Modifier(\"Instance\") {\n  '_Statement*;\n}",
        getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT
      ),
      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.class.any.initialization.blocks"),
        "{\n  '_Statement*;\n}",
        getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT
      ),

      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.enums"),
        "enum 'Enum {}",
        getClassType()
      ),

      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.class.with.parameterless.constructors"),
        "class 'Class {\n  '_Method{0,0}('_ParameterType '_Parameter+);\n}",
        getClassType()
      ),

      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.static.fields.without.final"),
        "static '_Type 'Variable+:[ script( \"!__context__.hasModifierProperty(\"final\")\" ) ] = '_Init?;",
        getClassType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT
      ),

      createSearchTemplateInfo(
        SSRBundle.message("predefined.configuration.interfaces.having.no.descendants"),
        "interface 'A:[script( \"com.intellij.psi.search.searches.ClassInheritorsSearch.search(__context__).findFirst() == null\" )] {}",
        getClassType()
      ),

      // Generics
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.generic.classes"), "class 'GenericClass<'_TypeParameter+> {} ",
                               getGenericsType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.generic.methods"), "<'_TypeParameter+> '_Type '_Method('_ParameterType '_Parameter*);",
                               getGenericsType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.typed.symbol"), "'Symbol <'_GenericArgument+>",
                               getGenericsType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.generic.casts"), "( '_Type <'_GenericArgument+> ) '_Expr",
                               getGenericsType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.type.var.substitutions.in.intanceof.with.generic.types"), "'_Expr instanceof '_Type <'Substitutions+> ",
                               getGenericsType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.variables.of.generic.types"), "'_Type <'_GenericArgument+>  'Var = '_Init?;",
                               getGenericsType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.diamond.operators"), "new 'Class<>('_Argument*)",
                               getGenericsType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.method.returns.bounded.wildcard"),
                               "[script( \"!Method.hasModifierProperty(com.intellij.psi.PsiModifier.ABSTRACT)\" )]'_Type<? extends '_Bound> 'Method('_ParameterType '_Parameter*);",
                               getGenericsType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.generic.constructors"), "<'_TypeParameter+> 'Class('_ParameterType '_Parameter*) {\n  '_Statement*;\n}",
                               getGenericsType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),

      // Add comments and metadata
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.comments"), "/* 'CommentContent */", getMetadataType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.javadoc.annotated.class"),
                               "/**\n" +
                               " * '_Comment\n" +
                               " * @'_Tag* '_TagValue*\n" +
                               " */\n" +
                               "class '_Class {\n}", getMetadataType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.javadoc.annotated.methods"),
                               "/**\n" +
                               " * '_Comment\n" +
                               " * @'_Tag* '_TagValue*\n" +
                               " */\n" +
                               "'_Type? '_Method('_ParameterType '_Parameter*);",
                               getMetadataType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.javadoc.annotated.fields"),
                               "/**\n" +
                               " * '_Comment\n" +
                               " * @'_Tag* '_TagValue*\n" +
                               " */\n" +
                               "'_Type+ 'Field+ = '_Init*;",
                               getMetadataType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.javadoc.tags"), "/** @'Tag+ '_TagValue* */", getMetadataType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.xdoclet.metadata"), "/** @'Tag \n  '_Property+\n*/",
                               getMetadataType()),

      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.annotations"), "@'_Annotation", getMetadataType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.annotated.class"),
                               "@'_Annotation\n" +
                               "class 'Class {}", getMetadataType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.annotated.fields"),
                               "@'_Annotation+\n" +
                               "'_FieldType 'Field+ = '_Init?;\n",
                               getMetadataType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.annotated.methods"),
                               "@'_Annotation+\n'_MethodType '_Method('_ParameterType '_Parameter*);",
                               getMetadataType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),

      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.not.annotated.methods"),
                               "@'_Annotation{0,0}\n'_MethodType '_Method('_ParameterType '_Parameter*);",
                               getMetadataType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),

      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.annotation.declarations"),
                               "@interface 'Interface {}", getMetadataType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.comments.containing.word"),
                               "// '_before bug '_after",
                               getMetadataType(), JavaFileType.INSTANCE),

      // J2EE templates
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.struts.1.1.actions"),"public class '_StrutsActionClass extends '_ParentClass*:Action {\n" +
                                                                                                      "  public ActionForward '_AnActionMethod:*execute (ActionMapping '_action,\n" +
                                                                                                      "                                 ActionForm '_form,\n" +
                                                                                                      "                                 HttpServletRequest '_request,\n" +
                                                                                                      "                                 HttpServletResponse '_response);\n" +
                                                                                                      "}", getJ2EEType()),
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.entity.ejb"),"class 'EntityBean implements EntityBean {\n" +
                                                                                              "  EntityContext '_Context?;\n\n" +
                                                                                              "  public void setEntityContext(EntityContext '_Context2);\n\n" +
                                                                                              "  public '_RetType ejbCreate('_CreateType '_CreateDcl*);\n" +
                                                                                              "  public void ejbActivate();\n\n" +
                                                                                              "  public void ejbLoad();\n\n" +
                                                                                              "  public void ejbPassivate();\n\n" +
                                                                                              "  public void ejbRemove();\n\n" +
                                                                                              "  public void ejbStore();\n" +
                                                                                              "}", getJ2EEType()),
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.session.ejb"),"class 'SessionBean implements SessionBean {\n" +
                                                                                               "  SessionContext '_Context?;\n\n" +
                                                                                               "  public void '_setSessionContext(SessionContext '_Context2);\n\n" +
                                                                                               "  public '_RetType ejbCreate('_CreateParameterType '_CreateParameterDcl*);\n" +
                                                                                               "  public void ejbActivate();\n\n" +
                                                                                               "  public void ejbPassivate();\n\n" +
                                                                                               "  public void ejbRemove();\n" +
                                                                                               "}", getJ2EEType()),
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.ejb.interface"),"interface 'EjbInterface extends EJBObject {\n" +
                                                                                                 "  '_Type '_Method+('_ParameterType '_Param*);\n" +
                                                                                                 "}", getJ2EEType()),
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.servlets"),"public class 'Servlet extends '_ParentClass:*HttpServlet {\n" +
                                                                                            "  public void '_InitServletMethod?:init ();\n" +
                                                                                            "  public void '_DestroyServletMethod?:destroy ();\n" +
                                                                                            "  void '_ServiceMethod?:*service (HttpServletRequest '_request, HttpServletResponse '_response);\n" +
                                                                                            "  void '_SpecificServiceMethod*:do.* (HttpServletRequest '_request2, HttpServletResponse '_response2); \n" +
                                                                                            "}", getJ2EEType()),
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.filters"),"public class 'Filter implements Filter {\n" +
                                                                                           "  public void '_DestroyFilterMethod?:*destroy ();\n" +
                                                                                           "  public void '_InitFilterMethod?:*init ();\n" +
                                                                                           "  public void '_FilteringMethod:*doFilter (ServletRequest '_request,\n" +
                                                                                           "    ServletResponse '_response,FilterChain '_chain);\n" +
                                                                                           "}", getJ2EEType()),

      // Misc types
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.serializable.classes.and.their.serialization.implementation"),
                               "class 'Class implements '_Serializable:*Serializable {\n" +
                               "  static final long '_VersionField?:serialVersionUID = '_VersionFieldInit?;\n" +
                               "  private static final ObjectStreamField[] '_persistentFields?:serialPersistentFields = '_persistentFieldInitial?; \n" +
                               "  private void '_SerializationWriteHandler?:writeObject (ObjectOutputStream '_stream) throws IOException;\n" +
                               "  private void '_SerializationReadHandler?:readObject (ObjectInputStream '_stream2) throws IOException, ClassNotFoundException;\n" +
                               "  Object '_SpecialSerializationReadHandler?:readResolve () throws ObjectStreamException;\n" +
                               "  Object '_SpecialSerializationWriteHandler?:writeReplace () throws ObjectStreamException;\n" +
                               "}", getMiscType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.cloneable.implementations"),
                               "class '_Class implements '_Interface:*Cloneable {\n" +
                               "  Object 'CloningMethod:*clone ();\n" +
                               "}", getMiscType()),
      createSearchTemplateInfoSimple(SSRBundle.message("predefined.configuration.]junit.test.cases"),
                                     "public class 'TestCase extends '_TestCaseClazz:*TestCase {\n" +
                                     "  public void '_testMethod+:test.* ();\n" +
                                     "}", getMiscType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.singletons"),
                               "class 'Class {\n" +
                               "  private 'Class('_ParameterType '_Parameter*) {\n" +
                               "   '_ConstructorStatement*;\n" +
                               "  }\n"+
                               "  private static '_Class '_Instance;\n" +
                               "  static '_Class '_GetInstance() {\n" +
                               "    '_SomeStatement*;\n" +
                               "    return '_Instance;\n" +
                               "  }\n"+
                               "}", getMiscType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.similar.methods.structure"),
                               "'_RetType '_Method('_ParameterType '_Parameter*) throws 'ExceptionType {\n" +
                               "  try {\n" +
                               "    '_OtherStatements+;\n" +
                               "  } catch('_SomeException '_Exception) {\n" +
                               "    '_CatchStatement*;\n" +
                               "    throw new 'ExceptionType('_ExceptionConstructorArgs*);\n" +
                               "  }\n" +
                               "}", getMiscType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.bean.info.classes"),
                               "class 'A implements '_:*java\\.beans\\.BeanInfo {\n" +
                               "}", getMiscType()),

      // interesting types
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.symbol"), "'Symbol", getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.fields.variables.read"),
                               "'Symbol:[ script( \"import com.intellij.psi.*\n" +
                               "import static com.intellij.psi.util.PsiUtil.*\n" +
                               "Symbol instanceof PsiReferenceExpression && isAccessedForReading(Symbol)\" ) ]", getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.fields_variables.with.given.name.pattern.updated"),
                               "'Symbol:[regex( name ) && script( \"import com.intellij.psi.*\n" +
                               "import static com.intellij.psi.util.PsiUtil.*\n" +
                               "Symbol instanceof PsiExpression && isAccessedForWriting(Symbol) ||\n" +
                               "  Symbol instanceof PsiVariable && Symbol.getInitializer() != null\" )]", getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.usage.of.derived.type.in.cast"), "('CastType:*[regex( Base )]) '_Expr",
                               getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.boxing.in.declarations"), "'_Type:Object|Integer|Boolean|Long|Character|Short|Byte 'Var = '_Value:[exprtype( int|boolean|long|char|short|byte )];",
                               getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.unboxing.in.declarations"), "'_Type:int|boolean|long|char|short|byte 'Var = '_Value:[exprtype( Integer|Boolean|Long|Character|Short|Byte )];",
                               getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.boxing.in.method.calls"), "'_Instance?.'Call('_BeforeParam*,'_Param:[ exprtype( int|boolean|long|char|short|byte ) && formal( Object|Integer|Boolean|Long|Character|Short|Byte )],'_AfterParam*)",
                               getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.unboxing.in.method.calls"), "'_Instance?.'Call('_BeforeParam*,'_Param:[ formal( int|boolean|long|char|short|byte ) && exprtype( Integer|Boolean|Long|Character|Short|Byte )],'_AfterParam*)",
                               getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.any.boxing"), "'_expression:[ exprtype( int|boolean|long|char|short|byte ) && formal( Object|Integer|Boolean|Long|Character|Short|Byte )]",
                               getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.any.unboxing"), "'_expression:[ formal( int|boolean|long|char|short|byte ) && exprtype( Integer|Boolean|Long|Character|Short|Byte )]",
                               getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.try.without.resources"), "try ('_ResourceType '_resource{0,0} = '_init; '_expression{0,0}) {\n  '_TryStatement*;\n} catch('_ExceptionType '_Exception{0,0}) {\n  '_CatchStatement*;\n}",
                               getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.switch.with.branches"), "[ script( \"import com.intellij.psi.*;\n" +
                                                                                                   "import com.intellij.psi.util.*;\n" +
                                                                                                   "PsiTreeUtil.getChildrenOfType(__context__.body, PsiSwitchLabelStatementBase.class).length < 5\" ) ]switch ('_expression) {\n}",
                               getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.labeled.break"), "break '_label;", getInterestingType()),
      createSearchTemplateInfo(SSRBundle.message("predefined.configuration.methods.with.final.parameters"),
                               "'_ReturnType? '_Method('_BeforeType '_BeforeParameter*, final '_ParameterType '_Parameter, '_AfterType '_AfterParameter*);",
                               getInterestingType(), JavaFileType.INSTANCE, JavaStructuralSearchProfile.MEMBER_CONTEXT),
      //createSearchTemplateInfo("methods called","'_?.'_:[ref('Method)] ('_*)", INTERESTING_TYPE),
      //createSearchTemplateInfo("fields selected","'_?.'_:[ref('Field)] ", INTERESTING_TYPE),
      //createSearchTemplateInfo("symbols used","'_:[ref('Symbol)] ", INTERESTING_TYPE),
      //createSearchTemplateInfo("types used","'_:[ref('Type)] '_;", INTERESTING_TYPE),

      createSearchTemplateInfo("xml attribute references java class", "<'_tag 'attribute=\"'_value:[ref( classes, interfaces & enums )]\"/>", SSRBundle.message("xml_html.category"), StdFileTypes.XML),
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
