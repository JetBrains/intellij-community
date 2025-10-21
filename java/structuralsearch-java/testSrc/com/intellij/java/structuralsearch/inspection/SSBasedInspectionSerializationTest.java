// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.structuralsearch.inspection;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolsSupplier;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.structuralsearch.inspection.SSBasedInspection;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.IOException;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class SSBasedInspectionSerializationTest extends LightJavaCodeInsightFixtureTestCase {

  /**
   * Ordered by modification time, with most recently modified pattern at the end. The order attribute needs to be written to define order between patterns part of a single inspection.
   */
  private static final String OLD_SETTINGS =
    """
      <inspection_tool>
        <replaceConfiguration name="DirectCallOfDispose" text="$Instance$.dispose()" recursive="false" caseInsensitive="false" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="Disposer.dispose($Instance$)">
          <constraint name="Instance" regexp="super" nameOfExprType="Disposable" withinHierarchy="true" exprTypeWithinHierarchy="true" minCount="0" negateName="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="File.createTempFile" text="java.io.File.createTempFile($prefix$, $suffix$)" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.util.io.FileUtil.createTempFile($prefix$, $suffix$)">
          <constraint name="prefix" within="" contains="" />
          <constraint name="suffix" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Consider explicit delete when file is not needed any more" text="$file$.deleteOnExit()" recursive="false" caseInsensitive="true" type="JAVA">
          <constraint name="file" nameOfExprType="java.io.File" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Use RecursionManager?" text="class $Class$ { &#10;  ThreadLocal&lt;$FieldType$&gt; $FieldName$ = $Init$;&#10;}" recursive="false" caseInsensitive="false" type="JAVA">
          <constraint name="Class" script="&quot;&quot;" within="" contains="" />
          <constraint name="FieldType" script="&quot;&quot;" regexp="Collection" withinHierarchy="true" maxCount="2147483647" target="true" wholeWordsOnly="true" within="" contains="" />
          <constraint name="FieldName" script="&quot;&quot;" maxCount="2147483647" within="" contains="" />
          <constraint name="Init" script="&quot;&quot;" minCount="0" maxCount="2147483647" within="" contains="" />
          <constraint name="__context__" script="&quot;&quot;" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="use NotificationGroup.toolWindowGroup().createNotification().notify() instead" text="$Instance$.notifyByBalloon($Parameter$)" recursive="false" caseInsensitive="false" type="JAVA">
          <constraint name="Instance" script="&quot;&quot;" nameOfExprType="com.intellij.openapi.wm.ToolWindowManager" exprTypeWithinHierarchy="true" minCount="0" within="" contains="" />
          <constraint name="Parameter" minCount="0" maxCount="2147483647" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="PsiManager.getInstance(psiElement.getProject())" text="com.intellij.psi.PsiManager.getInstance($psiElement$.getProject())" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="$psiElement$.getManager()">
          <constraint name="psiElement" script="&quot;&quot;" nameOfExprType="com\\.intellij\\.psi\\.PsiElement" exprTypeWithinHierarchy="true" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="String.getBytes() is current locale-dependant, use String.getBytes(Charset) instead" text="$s$.getBytes()" recursive="false" caseInsensitive="true" type="JAVA">
          <constraint name="s" script="&quot;&quot;" nameOfExprType="java.lang.String" exprTypeWithinHierarchy="true" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="searchable configurable should not contain JComponent fields" text="class $Class$ implements SearchableConfigurable{ &#10;  @Modifier(&quot;Instance&quot;) $FieldType$ $FieldName$ = $Init$;&#10;  public void disposeUIResources(){}&#10;}" recursive="false" caseInsensitive="false" type="JAVA">
          <constraint name="Class" script="&quot;&quot;" target="true" within="" contains="" />
          <constraint name="FieldType" script="&quot;&quot;" regexp="javax.swing.JComponent" withinHierarchy="true" formalTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="FieldName" script="&quot;&quot;" maxCount="2147483647" within="" contains="" />
          <constraint name="Init" script="&quot;&quot;" minCount="0" maxCount="2147483647" within="" contains="" />
          <constraint name="__context__" script="&quot;&quot;" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Suspicious usage of PsiUtilBase#findEditor inside quick fix, probably better idea to use LocalQuickFixAndIntentionActionOnPsiElement" text="$Instance$.$MethodCall$($Parameter$)" recursive="false" caseInsensitive="false" type="JAVA">
          <constraint name="Instance" script="&quot;&quot;" regexp="com.intellij.psi.util.PsiUtilBase" minCount="0" within="" contains="" />
          <constraint name="MethodCall" script="&quot;import com.intellij.psi.PsiClass&#10;import com.intellij.psi.util.InheritanceUtil&#10;import com.intellij.psi.util.PsiTreeUtil&#10;&#10;PsiClass aClass = PsiTreeUtil.getParentOfType(__context__, PsiClass.class)&#10;aClass != null &amp;&amp; InheritanceUtil.isInheritor(aClass, &quot;com.intellij.codeInspection.LocalQuickFix&quot;)&quot;" regexp="findEditor" target="true" within="" contains="" />
          <constraint name="Parameter" script="&quot;&quot;" minCount="0" maxCount="2147483647" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Potential memory leak: don't hold PsiElement inside quick fix, use SmartPsiElementPointer or instead of; also see LocalQuickFixOnPsiElement" text="class $Class$ { &#10;  $FieldType$ $FieldName$ = $Init$;&#10;}" recursive="false" caseInsensitive="false" type="JAVA">
          <constraint name="Class" script="&quot;import com.intellij.psi.PsiClass&#10;import com.intellij.psi.util.InheritanceUtil&#10;import com.intellij.psi.util.PsiTreeUtil&#10;&#10;Object c = __context__&#10;return c instanceof PsiClass &amp;&amp; InheritanceUtil.isInheritor(c, &quot;com.intellij.codeInspection.LocalQuickFix&quot;) &#10;&quot;" within="" contains="" />
          <constraint name="FieldType" script="&quot;import com.intellij.psi.PsiClass&#10;import com.intellij.psi.PsiElement&#10;import com.intellij.psi.PsiField&#10;import com.intellij.psi.util.InheritanceUtil&#10;import com.intellij.psi.util.PsiTreeUtil&#10;&#10;PsiField f = PsiTreeUtil.getParentOfType(__context__, PsiField)&#10;return f != null &amp;&amp; InheritanceUtil.isInheritor(f.getType(), &quot;com.intellij.psi.PsiElement&quot;) &#10;&quot;" maxCount="2147483647" within="" contains="" />
          <constraint name="FieldName" maxCount="2147483647" target="true" within="" contains="" />
          <constraint name="Init" minCount="0" maxCount="2147483647" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="Potential non-desired escape from lambda expression" text="PsiTreeUtil.getParentOfType($Parameter$, PsiMethod.class, true, PsiClass.class)" recursive="false" caseInsensitive="false" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" useStaticImport="true" replacement="PsiTreeUtil.getParentOfType($Parameter$, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class)">
          <constraint name="Parameter" script="&quot;&quot;" minCount="0" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="StringUtil.stripQuotesAroundValue" text="com.intellij.openapi.util.text.StringUtil.stripQuotesAroundValue($Parameter$)" recursive="false" caseInsensitive="false" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.util.text.StringUtil.unquoteString($Parameter$)">
          <constraint name="Parameter" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="new String(byte[]) is default locale dependent; use new String(byte[], Charset) instead" text="new String($b$)" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="new String($b$, com.intellij.openapi.vfs.CharsetToolkit.UTF8_CHARSET)">
          <constraint name="b" nameOfExprType="byte\\[\\]" exprTypeWithinHierarchy="true" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Anonymous threads are discouraged; please supply name" text="new Thread()" recursive="true" caseInsensitive="true" type="JAVA" />
        <replaceConfiguration name="&quot;var = volatile = E&quot; should be &quot;volatile = var = E&quot;" text="$var$ = $field$ = $e$;" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" useStaticImport="true" replacement="$field$ = $var$ = $e$;">
          <constraint name="e" within="" contains="" />
          <constraint name="var" within="" contains="" />
          <constraint name="field" script="&quot;import com.intellij.psi.PsiField&#10;import com.intellij.psi.PsiModifier&#10;import com.intellij.psi.PsiReferenceExpression&#10;import com.intellij.psi.PsiVariable&#10;&#10;field instanceof PsiReferenceExpression &amp;&amp;&#10;((PsiReferenceExpression)field).resolve() instanceof PsiField &amp;&amp;&#10;((PsiField)((PsiReferenceExpression)field).resolve()).hasModifierProperty(PsiModifier.VOLATILE) &amp;&amp;&#10;var instanceof PsiReferenceExpression &amp;&amp;&#10;((PsiReferenceExpression)var).resolve() instanceof PsiVariable &amp;&amp;&#10;!((PsiVariable)((PsiReferenceExpression)var).resolve()).hasModifierProperty(PsiModifier.VOLATILE)&quot;" within="" contains="" />
          <constraint name="__context__" target="true" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="StringUtil.indexOf(String, char) -&gt; String.indexOf(char)" text="com.intellij.openapi.util.text.StringUtil.indexOf($s$, $c$)" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="false" shortenFQN="false" replacement="$s$.indexOf($c$)">
          <constraint name="s" nameOfExprType="java.lang.String" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="c" nameOfExprType="char" exprTypeWithinHierarchy="true" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="TokenSet.create(TokenType.WHITE_SPACE) -&gt; TokenSet.WHITE_SPACE" text="com.intellij.psi.tree.TokenSet.create(com.intellij.psi.TokenType.WHITE_SPACE)" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.psi.tree.TokenSet.WHITE_SPACE" />
        <replaceConfiguration name="can be simplified to ReadAction.compute" created="1516639178225" text="$application$.runReadAction(new $Computable$() {&#10;  public $SearchScope$ compute() {&#10;    return $e$;&#10;  }&#10;})" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.application.ReadAction.compute(()-&gt;$e$)">
          <constraint name="SearchScope" within="" contains="" />
          <constraint name="e" within="" contains="" />
          <constraint name="application" nameOfExprType="com.intellij.openapi.application.Application" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="Computable" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="incorrect DumbAware check" created="1522680840725" text="$target$ instanceof DumbAware" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.project.DumbService.isDumbAware($target$)">
          <constraint name="target" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="non-static JamAnnotationMeta" created="1523014327447" text="class $Class$ {&#10;  $Type$ $Variable$ = $Init$;&#10;}" recursive="false" caseInsensitive="true" type="JAVA">
          <constraint name="Class" within="" contains="" />
          <constraint name="Type" regexp="JamAnnotationMeta" within="" contains="" />
          <constraint name="Variable" script="&quot;!__context__.hasModifierProperty(&quot;static&quot;)&quot;" maxCount="2147483647" target="true" within="" contains="" />
          <constraint name="Init" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="notNullize(s,&quot;&quot;) can be simplified" text="com.intellij.openapi.util.text.StringUtil.notNullize($s$, &quot;&quot;)" recursive="true" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.util.text.StringUtil.notNullize($s$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="s" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Suppressed exceptions are ignored" text="try {&#10;  $TryStatement$;&#10;} &#10;finally {&#10;  $s1$;&#10;  super.tearDown();&#10;  $s2$;&#10;}" recursive="true" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="try {&#10;  $TryStatement$;&#10;} &#10;catch (Throwable e) {&#10;  addSuppressedException(e);&#10;}&#10;finally {&#10;  $s1$; super.tearDown(); $s2$;&#10;}">
          <constraint name="__context__" script="com.intellij.psi.PsiTryStatement ts = com.intellij.psi.util.PsiTreeUtil.getParentOfType(__context__, com.intellij.psi.PsiTryStatement.class, false); return ts != null &amp;&amp; ts.getCatchSections().length == 0" within="" contains="" />
          <constraint name="TryStatement" maxCount="2147483647" within="" contains="" />
          <constraint name="s1" minCount="0" maxCount="2147483647" within="" contains="" />
          <constraint name="s2" minCount="0" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Too convoluted &quot;register(()-&gt;dispose())&quot; (wastes memory)" text="Disposer.register($myRoot$, () -&gt; Disposer.dispose($myFolder2$));" recursive="true" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="Disposer.register($myRoot$, $myFolder2$);">
          <constraint name="__context__" within="" contains="" />
          <constraint name="myRoot" within="" contains="" />
          <constraint name="myFolder2" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="use explicit toArray() method" text="com.intellij.util.ArrayUtil.toObjectArray($collection$, $class$.class)" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="false" shortenFQN="false" replacement="$collection$.toArray(new $class$[0])">
          <constraint name="__context__" within="" contains="" />
          <constraint name="class" within="" contains="" />
          <constraint name="collection" nameOfExprType="java\\.util\\.Collection" exprTypeWithinHierarchy="true" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Arrays.asList() is not immutable" text="public static final $Type$ $Field$ = java.util.Arrays.asList($elements$);" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="public static final $Type$ $Field$ = com.intellij.util.containers.ContainerUtil.immutableList($elements$);">
          <constraint name="__context__" within="" contains="" />
          <constraint name="Field" within="" contains="" />
          <constraint name="Type" within="" contains="" />
          <constraint name="elements" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="invalid use of ArrayUtil.contains: always returns false" text="com.intellij.util.ArrayUtil.contains($t$, $e$)" recursive="true" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="$e$.contains($t$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="t" within="" contains="" />
          <constraint name="e" nameOfExprType="java\\.util\\.Collection" exprTypeWithinHierarchy="true" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="DataKey.getData()" text="$Var2$.getData($Var3$.getDataContext())" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="false" shortenFQN="false" replacement="$Var3$.getData($Var2$)">
          <constraint name="__context__" target="true" within="" contains="" />
          <constraint name="Var2" nameOfExprType="DataKey" within="" contains="" />
          <constraint name="Var3" nameOfExprType="AnActionEvent" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Presentation setEnabledAndVisible" text="$Var1$.setEnabled($Var2$);&#10;$Var1$.setVisible($Var2$);&#10;" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="false" shortenFQN="false" replacement="$Var1$.setEnabledAndVisible($Var2$);">
          <constraint name="__context__" target="true" within="" contains="" />
          <constraint name="Var2" within="" contains="" />
          <constraint name="Var1" nameOfExprType="Presentation" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="super.update() in AnAction subclass" text="$super$.update($e$);" recursive="true" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="false" shortenFQN="false" replacement="">
          <constraint name="__context__" target="true" within="" contains="" />
          <constraint name="super" regexp="super" nameOfExprType="AnAction" within="" contains="" />
          <constraint name="e" nameOfExprType="AnActionEvent" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="SwingWorker generates too many threads; use Application.execute*() instead" text="javax.swing.SwingWorker" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="can simplify to getInstanceEx" text="(PsiManagerEx)PsiManager.getInstance($project$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="PsiManagerEx.getInstanceEx($project$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="project" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="submit() creates unnecessary Future" text="$e$.submit($d$);" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="$e$.execute($d$);">
          <constraint name="__context__" within="" contains="" />
          <constraint name="e" nameOfExprType="java\\.util\\.concurrent\\.ExecutorService" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="d" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="@HardwareAgentRequired should be applied only for performance tests" text="@com.intellij.idea.HardwareAgentRequired&#10;class $class$ {&#10;  &#10;}" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" within="" contains="" />
          <constraint name="class" regexp=".*Performance.*" negateName="true" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="remove test-only branch and use UIUtil.dispatchAllInvocationEvents in tests" text="if ($APP$.isUnitTestMode()) {&#10;  $DO$;&#10;} else {&#10;  $APP2$.$INV$($ARGS$);&#10;}" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" within="" contains="" />
          <constraint name="APP" within="" contains="" />
          <constraint name="APP2" within="" contains="" />
          <constraint name="INV" regexp="(invoke.*)|(.*Later.*)" within="" contains="" />
          <constraint name="ARGS" maxCount="2147483647" within="" contains="" />
          <constraint name="DO" maxCount="2147483647" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Logger.getInstance(unknown class)" text="Logger.getInstance(&quot;#$c$&quot;)" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.search.GlobalSearchScope&#10;import com.intellij.psi.util.PsiUtil&#10;&#10;def project = __context__.getProject();&#10;def fqn = c.getValue().substring(1);&#10;if (fqn.indexOf('.') == -1) return false;&#10;def shortName = com.intellij.openapi.util.text.StringUtil.getShortName(fqn);&#10;if (!shortName.equals(com.intellij.openapi.util.text.StringUtil.capitalize(shortName))) return false;&#10;&#10;def cClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));&#10; cClass == null&#10;&quot;" target="true" within="" contains="" />
          <constraint name="c" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="nullability for array elements specified, probably nullability for the array itself was intended" text="@$Anno$ $Type$ $x$;" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" within="" contains="" />
          <constraint name="x" within="" contains="" />
          <constraint name="Anno" regexp="Nullable|NotNull" target="true" within="" contains="" />
          <constraint name="Type" script="&quot;def parent = Type.parent;&#10;com.intellij.psi.util.PsiUtil.isLanguageLevel8OrHigher(Type) &amp;&amp;&#10;!parent.hasAnnotation(&quot;org.jetbrains.annotations.NotNull&quot;) &amp;&amp; &#10;!parent.hasAnnotation(&quot;org.jetbrains.annotations.Nullable&quot;);&quot;" regexp=".*(\\[\\])+" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="Replace explicit RExpression#getType with RTypeUtil#getType" text="((RExpression)$foo$).getType()" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="org.jetbrains.plugins.ruby.ruby.codeInsight.types.RTypeUtil.getType($foo$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="foo" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="nullability for array elements specified, probably nullability for the array itself was intended" order="1" text="@$Anno$ $Type$ $x$($T$ $param$);" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="member">
          <constraint name="__context__" within="" contains="" />
          <constraint name="x" within="" contains="" />
          <constraint name="Anno" regexp="Nullable|NotNull" target="true" within="" contains="" />
          <constraint name="Type" script="&quot;def parent = Type.parent;&#10;com.intellij.psi.util.PsiUtil.isLanguageLevel8OrHigher(Type) &amp;&amp;&#10;!parent.hasAnnotation(&quot;org.jetbrains.annotations.NotNull&quot;) &amp;&amp; &#10;!parent.hasAnnotation(&quot;org.jetbrains.annotations.Nullable&quot;);&quot;" regexp=".*(\\[\\])+" within="" contains="" />
          <constraint name="T" within="" contains="" />
          <constraint name="param" minCount="0" maxCount="2147483647" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Explicit cast to specific SwiftType" uuid="4728e433-2984-33d6-8696-3fd7f55d9252" description="a type can be a type alias or a single-item tuple of a desired type. And you most likely want to support them as well" suppressId="explicit.cast.to.swift.type" problemDescriptor="Please use `SwiftTypeUtil.as&lt;type&gt;` method instead" text="$ref$ instanceof $SwiftType$" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" within="" contains="" />
          <constraint name="ref" within="" contains="" />
          <constraint name="SwiftType" regexp="Swift.+Type" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="Manual TextRange.substring implementation" description="Reports code pattern like str.substring(range.getStartOffset(), range.getEndOffset()). This code could be replaced with range.substring(str)" problemDescriptor="TextRange.substring() could be used" text="$str$.substring($range$.getStartOffset(), $range$.getEndOffset())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="$range$.substring($str$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="str" nameOfExprType="java\\.lang\\.String" within="" contains="" />
          <constraint name="range" nameOfExprType="com\\.intellij\\.openapi\\.util\\.TextRange" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Method reference can slow down startup" description="Method references in class initializers cause qualifier class to be loaded as well, even if that reference would never be invoked" text="$C$::new" recursive="false" caseInsensitive="false" type="JAVA" pattern_context="default">
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.util.PsiTreeUtil &#10;&#10;def member = PsiTreeUtil.getParentOfType(__context__, PsiMember)&#10;if ((member instanceof PsiClassInitializer || member instanceof PsiField) &amp;&amp; member.hasModifierProperty(PsiModifier.STATIC)) {&#10;  def clazz = member.parent&#10;  def target = C.resolve()&#10;  def targetName = target?.qualifiedName&#10;  target != null &amp;&amp; clazz != target &amp;&amp; !target.qualifiedName.startsWith(&quot;java.&quot;)&#10;}&#10;else false&quot;" within="" contains="" />
          <constraint name="C" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Comparing Swift Types without context" description="Whether two types are equal depends on the context" suppressId="swift.type.equals" problemDescriptor="Use equalsInContext" text="$ref1$.equals($ref2$)" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" within="" contains="" />
          <constraint name="ref2" nameOfExprType="com\\.jetbrains\\.swift\\.psi\\.types\\.SwiftType" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="ref1" nameOfExprType="com\\.jetbrains\\.swift\\.psi\\.types\\.SwiftType" exprTypeWithinHierarchy="true" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="Over-dependence on fastutil Collections" uuid="8c028576-2d38-32d6-a013-7c3ce0e29f09" order="2" text="new ObjectOpenHashSet&lt;&gt;($a$)" recursive="false" caseInsensitive="false" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="com.intellij.util.containers.CollectionFactory.createSmallMemoryFootprintSet($a$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="a" minCount="0" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Over-dependence on fastutil Collections" uuid="8c028576-2d38-32d6-a013-7c3ce0e29f09" description="Factory method CollectionFactory.createXXX() should be used to reduce dependence on concrete collection implementation from third-party library " problemDescriptor="Should use factory method instead of direct ctr call" text="new Object2ObjectOpenHashMap&lt;&gt;($a$)" recursive="false" caseInsensitive="false" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="com.intellij.util.containers.CollectionFactory.createSmallMemoryFootprintMap($a$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="a" minCount="0" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Over-dependence on fastutil Collections" uuid="8c028576-2d38-32d6-a013-7c3ce0e29f09" order="1" text="new Object2ObjectLinkedOpenHashMap&lt;&gt;($a$)" recursive="false" caseInsensitive="false" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="com.intellij.util.containers.CollectionFactory.createSmallMemoryFootprintLinkedMap($a$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="a" minCount="0" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" description="stream statements create Stream instance while equivalent ContainerUtil methods aren't" problemDescriptor="can be replaced with ContainerUtil" text="Arrays.stream($m$).allMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.and($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="1" text="Arrays.stream($m$).anyMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.exists($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="2" text="Arrays.stream($m$).noneMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="!com.intellij.util.containers.ContainerUtil.exists($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="3" text="Stream.of($m$).allMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.and($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="4" text="Stream.of($m$).anyMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.or($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="5" text="Stream.of($m$).noneMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="!com.intellij.util.containers.ContainerUtil.exists($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="6" text="StreamEx.of($m$).allMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.and($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="7" text="StreamEx.of($m$).anyMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.or($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="8" text="StreamEx.of($m$).noneMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="!com.intellij.util.containers.ContainerUtil.exists($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="9" text="$f$.stream().map($m$).collect(Collectors.toList())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.map($f$, $m$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="f" within="" contains="" />
          <constraint name="m" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="10" text="Arrays.stream($f$).map($m$).collect(Collectors.toList())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.map($f$, $m$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="f" within="" contains="" />
          <constraint name="m" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="11" text="Stream.of($f$).map($m$).collect(Collectors.toList())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.map($f$, $m$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="f" within="" contains="" />
          <constraint name="m" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="12" text="StreamEx.of($f$).map($m$).collect(Collectors.toList())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.map($f$, $m$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="f" within="" contains="" />
          <constraint name="m" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="13" text="$l$.stream().filter($f$).collect(Collectors.toList())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.filter($l$, $f$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="l" within="" contains="" />
          <constraint name="f" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="14" text="Arrays.stream($l$).filter($f$).collect(Collectors.toList())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.filter($l$, $f$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="l" within="" contains="" />
          <constraint name="f" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="15" text="$l$.stream().filter($f$).findAny().orElse(null)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.find($l$, $f$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="l" within="" contains="" />
          <constraint name="f" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="16" text="$l$.stream().filter($f$).findFirst().orElse(null)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.find($l$, $f$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="l" within="" contains="" />
          <constraint name="f" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="17" text="Arrays.stream($l$).filter($f$).findAny().orElse(null)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.find($l$, $f$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="l" within="" contains="" />
          <constraint name="f" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" order="18" text="Arrays.stream($l$).filter($f$).findFirst().orElse(null)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.find($l$, $f$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="l" within="" contains="" />
          <constraint name="f" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" description="emptyCollection.toArray() creates new array whereas ArrayUtil.toArray() methods don't&#10;similarly, ArrayUtil.EMPTY_*_ARRAY don't allocate  " problemDescriptor="can use ArrayUtil" text="$collection$.toArray(new Object[$collection$.size()])" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.toObjectArray($collection$)">
          <constraint name="collection" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" order="1" text="$collection$.toArray(new Object[0])" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.toObjectArray($collection$)">
          <constraint name="collection" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" order="2" text="$collection$.toArray(ArrayUtil.EMPTY_OBJECT_ARRAY)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.toObjectArray($collection$)">
          <constraint name="collection" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" order="3" text="$collection$.toArray(new String[$collection$.size()])" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.toStringArray($collection$)">
          <constraint name="collection" nameOfExprType="java\\.util\\.Collection" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" order="4" text="$collection$.toArray(new String[0])" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.toStringArray($collection$)">
          <constraint name="collection" nameOfExprType="java\\.util\\.Collection" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" order="5" text="$collection$.toArray(ArrayUtil.EMPTY_STRING_ARRAY)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.toStringArray($collection$)">
          <constraint name="collection" nameOfExprType="java\\.util\\.Collection" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" order="6" text="new Class[0]" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.EMPTY_CLASS_ARRAY">
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" order="7" text="new Object[0]" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.EMPTY_OBJECT_ARRAY">
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" order="8" text="new String[0]" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY">
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use StringUtil" uuid="e2009598-65b8-3ae6-a9f8-9159d2b2e27a" text="if ($s$.endsWith($e$)) {&#10;  $s$ = $s$.substring(0, $s$.length() - $e$.length());&#10;}" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="$s$ = com.intellij.openapi.util.text.StringUtil.trimEnd($s$, $e$);&#10;">
          <constraint name="s" within="" contains="" />
          <constraint name="e" within="" contains="" />
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.search.GlobalSearchScope&#10;import com.intellij.psi.util.PsiUtil&#10;&#10;def stringUtil = JavaPsiFacade.getInstance(e.getProject()).findClass(&quot;com.intellij.openapi.util.text.StringUtil&quot;, GlobalSearchScope.allScope(e.getProject()));&#10;def strVirtualFile = PsiUtil.getVirtualFile(stringUtil);&#10;&#10;e instanceof PsiLiteralExpression &amp;&amp;&#10;e.getParent().getParent().getParent() instanceof PsiIfStatement &amp;&amp;&#10;((PsiIfStatement)e.getParent().getParent().getParent()).getElseBranch() == null &amp;&amp;&#10;strVirtualFile != null &amp;&amp;  &#10;e.getResolveScope().contains(strVirtualFile)&#10;&#10;&quot;" target="true" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use StringUtil" uuid="e2009598-65b8-3ae6-a9f8-9159d2b2e27a" order="1" text="if ($s$.endsWith(&quot;$e$&quot;)) {&#10;  $s$ = $s$.substring(0,$s$.length()-$n$);&#10;}" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="$s$ = com.intellij.openapi.util.text.StringUtil.trimEnd($s$, &quot;$e$&quot;);&#10;">
          <constraint name="s" within="" contains="" />
          <constraint name="e" within="" contains="" />
          <constraint name="n" within="" contains="" />
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.search.GlobalSearchScope&#10;import com.intellij.psi.util.PsiUtil&#10;&#10;def stringUtil = JavaPsiFacade.getInstance(e.getProject()).findClass(&quot;com.intellij.openapi.util.text.StringUtil&quot;, GlobalSearchScope.allScope(e.getProject()));&#10;def strVirtualFile = PsiUtil.getVirtualFile(stringUtil);&#10;&#10;e instanceof PsiLiteralExpression &amp;&amp;&#10;n instanceof PsiLiteralExpression &amp;&amp;&#10;((PsiLiteralExpression)e).getValue() instanceof String &amp;&amp;&#10;((PsiLiteralExpression)n).getValue() instanceof Integer &amp;&amp;&#10;((String)((PsiLiteralExpression)e).getValue()).length() ==&#10;((Integer)((PsiLiteralExpression)n).getValue()).intValue() &amp;&amp;&#10;e.getParent().getParent().getParent() instanceof PsiIfStatement &amp;&amp;&#10;((PsiIfStatement)e.getParent().getParent().getParent()).getElseBranch() == null &amp;&amp;  &#10;strVirtualFile != null &amp;&amp;  &#10;e.getResolveScope().contains(strVirtualFile)&quot;" target="true" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use StringUtil" uuid="e2009598-65b8-3ae6-a9f8-9159d2b2e27a" order="2" text="if ($s$.startsWith($e$)) {&#10;  $s$ = $s$.substring($e$.length());&#10;}" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="$s$ = com.intellij.openapi.util.text.StringUtil.trimStart($s$, $e$);&#10;">
          <constraint name="s" within="" contains="" />
          <constraint name="e" within="" contains="" />
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.search.GlobalSearchScope&#10;import com.intellij.psi.util.PsiUtil&#10;&#10;def stringUtil = JavaPsiFacade.getInstance(e.getProject()).findClass(&quot;com.intellij.openapi.util.text.StringUtil&quot;, GlobalSearchScope.allScope(e.getProject()));&#10;def strVirtualFile = PsiUtil.getVirtualFile(stringUtil);&#10;&#10;e.getParent().getParent().getParent() instanceof PsiIfStatement &amp;&amp;&#10;((PsiIfStatement)e.getParent().getParent().getParent()).getElseBranch() == null &amp;&amp;&#10;strVirtualFile != null &amp;&amp;  &#10;e.getContainingFile().getResolveScope().contains(strVirtualFile)&#10;&quot;" target="true" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use StringUtil" uuid="e2009598-65b8-3ae6-a9f8-9159d2b2e27a" order="3" text="if ($s$.startsWith(&quot;$e$&quot;)) {&#10;  $s$ = $s$.substring($n$);&#10;}" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="$s$ = com.intellij.openapi.util.text.StringUtil.trimStart($s$, &quot;$e$&quot;);&#10;">
          <constraint name="s" within="" contains="" />
          <constraint name="e" within="" contains="" />
          <constraint name="n" within="" contains="" />
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.search.GlobalSearchScope&#10;import com.intellij.psi.util.PsiUtil&#10;&#10;def stringUtil = JavaPsiFacade.getInstance(e.getProject()).findClass(&quot;com.intellij.openapi.util.text.StringUtil&quot;, GlobalSearchScope.allScope(e.getProject()));&#10;def strVirtualFile = PsiUtil.getVirtualFile(stringUtil);&#10;&#10;e instanceof PsiLiteralExpression &amp;&amp;&#10;n instanceof PsiLiteralExpression &amp;&amp;&#10;((PsiLiteralExpression)e).getValue() instanceof String &amp;&amp;&#10;((PsiLiteralExpression)n).getValue() instanceof Integer &amp;&amp;&#10;((String)((PsiLiteralExpression)e).getValue()).length() ==&#10;((Integer)((PsiLiteralExpression)n).getValue()).intValue() &amp;&amp;&#10;e.getParent().getParent().getParent() instanceof PsiIfStatement &amp;&amp;&#10;((PsiIfStatement)e.getParent().getParent().getParent()).getElseBranch() == null &amp;&amp;  &#10;strVirtualFile != null &amp;&amp;  &#10;e.getResolveScope().contains(strVirtualFile)&quot;" target="true" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="File.createTempFile" order="1" text="java.io.File.createTempFile($prefix$, $suffix$, $dir$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.util.io.FileUtil.createTempFile($dir$, $prefix$, $suffix$, true)">
          <constraint name="prefix" within="" contains="" />
          <constraint name="suffix" within="" contains="" />
          <constraint name="dir" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="DBE model property must be public" uuid="0899a4c2-78e7-3f01-be3c-9c46451baec3" text="class $Class$ {&#10;  @StateProperty&#10;  @Modifier(&quot;packageLocal&quot;) $FieldType$ $FieldName$;&#10;}" recursive="true" caseInsensitive="true" type="JAVA">
          <constraint name="Class" within="" contains="" />
          <constraint name="FieldType" within="" contains="" />
          <constraint name="FieldName" target="true" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="DBE model property must be public" uuid="0899a4c2-78e7-3f01-be3c-9c46451baec3" order="1" text="class $Class$ {&#10;  @StateProperty&#10;  private $FieldType$ $FieldName$;&#10;}" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="false" replacement="class $Class$ {&#10;  @StateProperty&#10;  private $FieldType$ $FieldName$;&#10;}">
          <constraint name="Class" within="" contains="" />
          <constraint name="FieldType" within="" contains="" />
          <constraint name="FieldName" target="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="DBE model property must be public" uuid="0899a4c2-78e7-3f01-be3c-9c46451baec3" order="2" text="class $Class$ {&#10;  @StateProperty&#10;  protected $FieldType$ $FieldName$;&#10;}" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="false" replacement="class $Class$ {&#10;  @StateProperty&#10;  protected $FieldType$ $FieldName$;&#10;}">
          <constraint name="Class" within="" contains="" />
          <constraint name="FieldType" within="" contains="" />
          <constraint name="FieldName" target="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Anonymous threads are discouraged; please supply name" order="1" text="Executors.newSingleThreadScheduledExecutor()" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ConcurrencyUtil.newSingleScheduledThreadExecutor()">
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Anonymous threads are discouraged; please supply name" order="2" text="Executors.newSingleThreadExecutor()" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ConcurrencyUtil.newSingleThreadExecutor()">
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Anonymous threads are discouraged; please supply name" order="3" text="new Thread($runnable$)" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="runnable" nameOfExprType="java.lang.Runnable" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="Presentation setEnabledAndVisible" order="1" text="$Var1$.setVisible($Var2$);&#10;$Var1$.setEnabled($Var2$);&#10;" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="false" replacement="$Var1$.setEnabledAndVisible($Var2$);">
          <constraint name="__context__" target="true" within="" contains="" />
          <constraint name="Var2" within="" contains="" />
          <constraint name="Var1" nameOfExprType="Presentation" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="remove test-only branch and use UIUtil.dispatchAllInvocationEvents in tests" order="1" text="if (!$APP$.isUnitTestMode()) {&#10;  $APP2$.$INV$($ARGS$);&#10;} else {&#10;  $DO$;&#10;}" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="false" replacement="if (!$APP$.isUnitTestMode()) {&#10;  $APP2$.$INV$($ARGS$);&#10;} else {&#10;  $DO$;&#10;}">
          <constraint name="__context__" within="" contains="" />
          <constraint name="APP" within="" contains="" />
          <constraint name="APP2" within="" contains="" />
          <constraint name="INV" regexp="(invoke.*)|(.*Later.*)" within="" contains="" />
          <constraint name="ARGS" maxCount="2147483647" within="" contains="" />
          <constraint name="DO" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can be simplified to ReadAction.compute" order="1" text="$application$.runReadAction(($C$)()-&gt;$e$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.application.ReadAction.compute(()-&gt;$e$)">
          <constraint name="e" within="" contains="" />
          <constraint name="C" minCount="0" within="" contains="" />
          <constraint name="application" nameOfExprType="com.intellij.openapi.application.Application" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ContainerUtil" uuid="c9f1d44f-a345-34e5-8460-cf58d74c993d" text="new HashSet&lt;&gt;(Arrays.asList($list$))" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.set($list$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="list" maxCount="32000" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ContainerUtil" uuid="c9f1d44f-a345-34e5-8460-cf58d74c993d" order="1" text="Collections.unmodifiableList(Arrays.asList($c$))" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.immutableList($c$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="c" maxCount="32000" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Logger expression can be simplified" uuid="0befec03-d2b3-3838-ada0-363adc05fac7" text="Logger.getInstance(&quot;#$c$&quot;)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="Logger.getInstance($c$.class)">
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.search.GlobalSearchScope&#10;import com.intellij.psi.util.PsiUtil&#10;&#10;def project = __context__.getProject();&#10;def cClass = JavaPsiFacade.getInstance(project).findClass(c.getValue().substring(1), GlobalSearchScope.allScope(project));&#10;def cVirtualFile = c==null?null:PsiUtil.getVirtualFile(cClass);&#10;&#10;cVirtualFile != null &amp;&amp;  &#10;__context__.getContainingFile().getResolveScope().contains(cVirtualFile)&#10;&quot;" target="true" within="" contains="" />
          <constraint name="c" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Logger expression can be simplified" uuid="0befec03-d2b3-3838-ada0-363adc05fac7" order="1" text="com.intellij.openapi.diagnostic.Logger.getInstance(&quot;#&quot; + $c$.class.getName())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.diagnostic.Logger.getInstance($c$.class)">
          <constraint name="c" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Logger expression can be simplified" uuid="0befec03-d2b3-3838-ada0-363adc05fac7" order="2" text="$LOG$.assertTrue(false, $e$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="$LOG$.error($e$)">
          <constraint name="e" within="" contains="" />
          <constraint name="LOG" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Anonymous threads are discouraged; please supply name" order="4" text="new java.util.Timer()" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" target="true" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Anonymous threads are discouraged; please supply name" order="5" text="new java.util.Timer($b$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" target="true" within="" contains="" />
          <constraint name="b" nameOfExprType="boolean" exprTypeWithinHierarchy="true" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="findAnnotation(name) != null -&gt; hasAnnotation(name)" text="$Instance$.findAnnotation($Parameter$) != null" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="false" shortenFQN="false" replacement="$Instance$.hasAnnotation($Parameter$)">
          <constraint name="Instance" nameOfExprType="com\\.intellij\\.psi\\.PsiAnnotationOwner" exprTypeWithinHierarchy="true" minCount="0" within="" contains="" />
          <constraint name="Parameter" minCount="0" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="findAnnotation(name) can be simplified" uuid="7228acac-afb8-381c-b31f-ad9cdefe3a81" order="1" text="$Instance$.findAnnotation($Parameter$) == null&#10;" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="false" replacement="!$Instance$.hasAnnotation($Parameter$)">
          <constraint name="Instance" nameOfExprType="com\\.intellij\\.psi\\.PsiAnnotationOwner" exprTypeWithinHierarchy="true" minCount="0" within="" contains="" />
          <constraint name="Parameter" minCount="0" maxCount="2147483647" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Storing localized strings in static fields prevents dynamic loading of language bundles" text="$bundle$.$message$(&quot;$s$&quot;, $p$)" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" script="&quot;import com.intellij.psi.*;&#10;import com.intellij.psi.util.*;&#10;PsiElement member = PsiTreeUtil.getParentOfType(__context__, PsiMember.class, PsiLambdaExpression.class)&#10;return member instanceof PsiField &amp;&amp; member.hasModifierProperty(PsiModifier.STATIC)&quot;" within="" contains="" />
          <constraint name="s" within="" contains="" />
          <constraint name="bundle" regexp="AbstractBundle" withinHierarchy="true" within="" contains="" />
          <constraint name="p" minCount="0" maxCount="2147483647" within="" contains="" />
          <constraint name="message" regexp="message" target="true" within="" contains="" />
        </searchConfiguration>
      </inspection_tool>""";

  /**
   * Ordered like in UI by name and pattern order. No order attribute written, because it is not necessary.
   */
  private static final String NEW_SETTINGS =
    """
      <inspection_tool>
        <replaceConfiguration name="&quot;var = volatile = E&quot; should be &quot;volatile = var = E&quot;" text="$var$ = $field$ = $e$;" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" useStaticImport="true" replacement="$field$ = $var$ = $e$;">
          <constraint name="e" within="" contains="" />
          <constraint name="var" within="" contains="" />
          <constraint name="field" script="&quot;import com.intellij.psi.PsiField&#10;import com.intellij.psi.PsiModifier&#10;import com.intellij.psi.PsiReferenceExpression&#10;import com.intellij.psi.PsiVariable&#10;&#10;field instanceof PsiReferenceExpression &amp;&amp;&#10;((PsiReferenceExpression)field).resolve() instanceof PsiField &amp;&amp;&#10;((PsiField)((PsiReferenceExpression)field).resolve()).hasModifierProperty(PsiModifier.VOLATILE) &amp;&amp;&#10;var instanceof PsiReferenceExpression &amp;&amp;&#10;((PsiReferenceExpression)var).resolve() instanceof PsiVariable &amp;&amp;&#10;!((PsiVariable)((PsiReferenceExpression)var).resolve()).hasModifierProperty(PsiModifier.VOLATILE)&quot;" within="" contains="" />
          <constraint name="__context__" target="true" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="@HardwareAgentRequired should be applied only for performance tests" text="@com.intellij.idea.HardwareAgentRequired&#10;class $class$ {&#10;  &#10;}" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" within="" contains="" />
          <constraint name="class" regexp=".*Performance.*" negateName="true" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Anonymous threads are discouraged; please supply name" text="new Thread()" recursive="true" caseInsensitive="true" type="JAVA" />
        <replaceConfiguration name="Anonymous threads are discouraged; please supply name" text="Executors.newSingleThreadScheduledExecutor()" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ConcurrencyUtil.newSingleScheduledThreadExecutor()">
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Anonymous threads are discouraged; please supply name" text="Executors.newSingleThreadExecutor()" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ConcurrencyUtil.newSingleThreadExecutor()">
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Anonymous threads are discouraged; please supply name" text="new Thread($runnable$)" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="runnable" nameOfExprType="java.lang.Runnable" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Anonymous threads are discouraged; please supply name" text="new java.util.Timer()" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" target="true" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Anonymous threads are discouraged; please supply name" text="new java.util.Timer($b$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" target="true" within="" contains="" />
          <constraint name="b" nameOfExprType="boolean" exprTypeWithinHierarchy="true" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="Arrays.asList() is not immutable" text="public static final $Type$ $Field$ = java.util.Arrays.asList($elements$);" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="public static final $Type$ $Field$ = com.intellij.util.containers.ContainerUtil.immutableList($elements$);">
          <constraint name="__context__" within="" contains="" />
          <constraint name="Field" within="" contains="" />
          <constraint name="Type" within="" contains="" />
          <constraint name="elements" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can be simplified to ReadAction.compute" created="1516639178225" text="$application$.runReadAction(new $Computable$() {&#10;  public $SearchScope$ compute() {&#10;    return $e$;&#10;  }&#10;})" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.application.ReadAction.compute(()-&gt;$e$)">
          <constraint name="SearchScope" within="" contains="" />
          <constraint name="e" within="" contains="" />
          <constraint name="application" nameOfExprType="com.intellij.openapi.application.Application" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="Computable" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can be simplified to ReadAction.compute" text="$application$.runReadAction(($C$)()-&gt;$e$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.application.ReadAction.compute(()-&gt;$e$)">
          <constraint name="e" within="" contains="" />
          <constraint name="C" minCount="0" within="" contains="" />
          <constraint name="application" nameOfExprType="com.intellij.openapi.application.Application" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can simplify to getInstanceEx" text="(PsiManagerEx)PsiManager.getInstance($project$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="PsiManagerEx.getInstanceEx($project$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="project" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" description="emptyCollection.toArray() creates new array whereas ArrayUtil.toArray() methods don't&#10;similarly, ArrayUtil.EMPTY_*_ARRAY don't allocate  " problemDescriptor="can use ArrayUtil" text="$collection$.toArray(new Object[$collection$.size()])" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.toObjectArray($collection$)">
          <constraint name="collection" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" text="$collection$.toArray(new Object[0])" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.toObjectArray($collection$)">
          <constraint name="collection" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" text="$collection$.toArray(ArrayUtil.EMPTY_OBJECT_ARRAY)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.toObjectArray($collection$)">
          <constraint name="collection" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" text="$collection$.toArray(new String[$collection$.size()])" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.toStringArray($collection$)">
          <constraint name="collection" nameOfExprType="java\\.util\\.Collection" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" text="$collection$.toArray(new String[0])" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.toStringArray($collection$)">
          <constraint name="collection" nameOfExprType="java\\.util\\.Collection" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" text="$collection$.toArray(ArrayUtil.EMPTY_STRING_ARRAY)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.toStringArray($collection$)">
          <constraint name="collection" nameOfExprType="java\\.util\\.Collection" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" text="new Class[0]" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.EMPTY_CLASS_ARRAY">
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" text="new Object[0]" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.EMPTY_OBJECT_ARRAY">
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ArrayUtil" uuid="d28d1bfc-45ff-3472-864c-6e13c1d1688d" text="new String[0]" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY">
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ContainerUtil" uuid="c9f1d44f-a345-34e5-8460-cf58d74c993d" text="new HashSet&lt;&gt;(Arrays.asList($list$))" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.set($list$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="list" maxCount="32000" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use ContainerUtil" uuid="c9f1d44f-a345-34e5-8460-cf58d74c993d" text="Collections.unmodifiableList(Arrays.asList($c$))" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.immutableList($c$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="c" maxCount="32000" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use StringUtil" uuid="e2009598-65b8-3ae6-a9f8-9159d2b2e27a" text="if ($s$.endsWith($e$)) {&#10;  $s$ = $s$.substring(0, $s$.length() - $e$.length());&#10;}" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="$s$ = com.intellij.openapi.util.text.StringUtil.trimEnd($s$, $e$);&#10;">
          <constraint name="s" within="" contains="" />
          <constraint name="e" within="" contains="" />
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.search.GlobalSearchScope&#10;import com.intellij.psi.util.PsiUtil&#10;&#10;def stringUtil = JavaPsiFacade.getInstance(e.getProject()).findClass(&quot;com.intellij.openapi.util.text.StringUtil&quot;, GlobalSearchScope.allScope(e.getProject()));&#10;def strVirtualFile = PsiUtil.getVirtualFile(stringUtil);&#10;&#10;e instanceof PsiLiteralExpression &amp;&amp;&#10;e.getParent().getParent().getParent() instanceof PsiIfStatement &amp;&amp;&#10;((PsiIfStatement)e.getParent().getParent().getParent()).getElseBranch() == null &amp;&amp;&#10;strVirtualFile != null &amp;&amp;  &#10;e.getResolveScope().contains(strVirtualFile)&#10;&#10;&quot;" target="true" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use StringUtil" uuid="e2009598-65b8-3ae6-a9f8-9159d2b2e27a" text="if ($s$.endsWith(&quot;$e$&quot;)) {&#10;  $s$ = $s$.substring(0,$s$.length()-$n$);&#10;}" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="$s$ = com.intellij.openapi.util.text.StringUtil.trimEnd($s$, &quot;$e$&quot;);&#10;">
          <constraint name="s" within="" contains="" />
          <constraint name="e" within="" contains="" />
          <constraint name="n" within="" contains="" />
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.search.GlobalSearchScope&#10;import com.intellij.psi.util.PsiUtil&#10;&#10;def stringUtil = JavaPsiFacade.getInstance(e.getProject()).findClass(&quot;com.intellij.openapi.util.text.StringUtil&quot;, GlobalSearchScope.allScope(e.getProject()));&#10;def strVirtualFile = PsiUtil.getVirtualFile(stringUtil);&#10;&#10;e instanceof PsiLiteralExpression &amp;&amp;&#10;n instanceof PsiLiteralExpression &amp;&amp;&#10;((PsiLiteralExpression)e).getValue() instanceof String &amp;&amp;&#10;((PsiLiteralExpression)n).getValue() instanceof Integer &amp;&amp;&#10;((String)((PsiLiteralExpression)e).getValue()).length() ==&#10;((Integer)((PsiLiteralExpression)n).getValue()).intValue() &amp;&amp;&#10;e.getParent().getParent().getParent() instanceof PsiIfStatement &amp;&amp;&#10;((PsiIfStatement)e.getParent().getParent().getParent()).getElseBranch() == null &amp;&amp;  &#10;strVirtualFile != null &amp;&amp;  &#10;e.getResolveScope().contains(strVirtualFile)&quot;" target="true" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use StringUtil" uuid="e2009598-65b8-3ae6-a9f8-9159d2b2e27a" text="if ($s$.startsWith($e$)) {&#10;  $s$ = $s$.substring($e$.length());&#10;}" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="$s$ = com.intellij.openapi.util.text.StringUtil.trimStart($s$, $e$);&#10;">
          <constraint name="s" within="" contains="" />
          <constraint name="e" within="" contains="" />
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.search.GlobalSearchScope&#10;import com.intellij.psi.util.PsiUtil&#10;&#10;def stringUtil = JavaPsiFacade.getInstance(e.getProject()).findClass(&quot;com.intellij.openapi.util.text.StringUtil&quot;, GlobalSearchScope.allScope(e.getProject()));&#10;def strVirtualFile = PsiUtil.getVirtualFile(stringUtil);&#10;&#10;e.getParent().getParent().getParent() instanceof PsiIfStatement &amp;&amp;&#10;((PsiIfStatement)e.getParent().getParent().getParent()).getElseBranch() == null &amp;&amp;&#10;strVirtualFile != null &amp;&amp;  &#10;e.getContainingFile().getResolveScope().contains(strVirtualFile)&#10;&quot;" target="true" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="can use StringUtil" uuid="e2009598-65b8-3ae6-a9f8-9159d2b2e27a" text="if ($s$.startsWith(&quot;$e$&quot;)) {&#10;  $s$ = $s$.substring($n$);&#10;}" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="$s$ = com.intellij.openapi.util.text.StringUtil.trimStart($s$, &quot;$e$&quot;);&#10;">
          <constraint name="s" within="" contains="" />
          <constraint name="e" within="" contains="" />
          <constraint name="n" within="" contains="" />
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.search.GlobalSearchScope&#10;import com.intellij.psi.util.PsiUtil&#10;&#10;def stringUtil = JavaPsiFacade.getInstance(e.getProject()).findClass(&quot;com.intellij.openapi.util.text.StringUtil&quot;, GlobalSearchScope.allScope(e.getProject()));&#10;def strVirtualFile = PsiUtil.getVirtualFile(stringUtil);&#10;&#10;e instanceof PsiLiteralExpression &amp;&amp;&#10;n instanceof PsiLiteralExpression &amp;&amp;&#10;((PsiLiteralExpression)e).getValue() instanceof String &amp;&amp;&#10;((PsiLiteralExpression)n).getValue() instanceof Integer &amp;&amp;&#10;((String)((PsiLiteralExpression)e).getValue()).length() ==&#10;((Integer)((PsiLiteralExpression)n).getValue()).intValue() &amp;&amp;&#10;e.getParent().getParent().getParent() instanceof PsiIfStatement &amp;&amp;&#10;((PsiIfStatement)e.getParent().getParent().getParent()).getElseBranch() == null &amp;&amp;  &#10;strVirtualFile != null &amp;&amp;  &#10;e.getResolveScope().contains(strVirtualFile)&quot;" target="true" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Comparing Swift Types without context" description="Whether two types are equal depends on the context" suppressId="swift.type.equals" problemDescriptor="Use equalsInContext" text="$ref1$.equals($ref2$)" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" within="" contains="" />
          <constraint name="ref2" nameOfExprType="com\\.jetbrains\\.swift\\.psi\\.types\\.SwiftType" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="ref1" nameOfExprType="com\\.jetbrains\\.swift\\.psi\\.types\\.SwiftType" exprTypeWithinHierarchy="true" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Consider explicit delete when file is not needed any more" text="$file$.deleteOnExit()" recursive="false" caseInsensitive="true" type="JAVA">
          <constraint name="file" nameOfExprType="java.io.File" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="DataKey.getData()" text="$Var2$.getData($Var3$.getDataContext())" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="false" shortenFQN="false" replacement="$Var3$.getData($Var2$)">
          <constraint name="__context__" target="true" within="" contains="" />
          <constraint name="Var2" nameOfExprType="DataKey" within="" contains="" />
          <constraint name="Var3" nameOfExprType="AnActionEvent" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="DBE model property must be public" uuid="0899a4c2-78e7-3f01-be3c-9c46451baec3" text="class $Class$ {&#10;  @StateProperty&#10;  @Modifier(&quot;packageLocal&quot;) $FieldType$ $FieldName$;&#10;}" recursive="true" caseInsensitive="true" type="JAVA">
          <constraint name="Class" within="" contains="" />
          <constraint name="FieldType" within="" contains="" />
          <constraint name="FieldName" target="true" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="DBE model property must be public" uuid="0899a4c2-78e7-3f01-be3c-9c46451baec3" text="class $Class$ {&#10;  @StateProperty&#10;  private $FieldType$ $FieldName$;&#10;}" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="false" replacement="class $Class$ {&#10;  @StateProperty&#10;  private $FieldType$ $FieldName$;&#10;}">
          <constraint name="Class" within="" contains="" />
          <constraint name="FieldType" within="" contains="" />
          <constraint name="FieldName" target="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="DBE model property must be public" uuid="0899a4c2-78e7-3f01-be3c-9c46451baec3" text="class $Class$ {&#10;  @StateProperty&#10;  protected $FieldType$ $FieldName$;&#10;}" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="false" replacement="class $Class$ {&#10;  @StateProperty&#10;  protected $FieldType$ $FieldName$;&#10;}">
          <constraint name="Class" within="" contains="" />
          <constraint name="FieldType" within="" contains="" />
          <constraint name="FieldName" target="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="DirectCallOfDispose" text="$Instance$.dispose()" recursive="false" caseInsensitive="false" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="Disposer.dispose($Instance$)">
          <constraint name="Instance" regexp="super" nameOfExprType="Disposable" withinHierarchy="true" exprTypeWithinHierarchy="true" minCount="0" negateName="true" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Explicit cast to specific SwiftType" uuid="4728e433-2984-33d6-8696-3fd7f55d9252" description="a type can be a type alias or a single-item tuple of a desired type. And you most likely want to support them as well" suppressId="explicit.cast.to.swift.type" problemDescriptor="Please use `SwiftTypeUtil.as&lt;type&gt;` method instead" text="$ref$ instanceof $SwiftType$" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" within="" contains="" />
          <constraint name="ref" within="" contains="" />
          <constraint name="SwiftType" regexp="Swift.+Type" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="File.createTempFile" text="java.io.File.createTempFile($prefix$, $suffix$)" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.util.io.FileUtil.createTempFile($prefix$, $suffix$)">
          <constraint name="prefix" within="" contains="" />
          <constraint name="suffix" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="File.createTempFile" text="java.io.File.createTempFile($prefix$, $suffix$, $dir$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.util.io.FileUtil.createTempFile($dir$, $prefix$, $suffix$, true)">
          <constraint name="prefix" within="" contains="" />
          <constraint name="suffix" within="" contains="" />
          <constraint name="dir" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="findAnnotation(name) != null -&gt; hasAnnotation(name)" text="$Instance$.findAnnotation($Parameter$) != null" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="false" shortenFQN="false" replacement="$Instance$.hasAnnotation($Parameter$)">
          <constraint name="Instance" nameOfExprType="com\\.intellij\\.psi\\.PsiAnnotationOwner" exprTypeWithinHierarchy="true" minCount="0" within="" contains="" />
          <constraint name="Parameter" minCount="0" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="findAnnotation(name) can be simplified" uuid="7228acac-afb8-381c-b31f-ad9cdefe3a81" text="$Instance$.findAnnotation($Parameter$) == null&#10;" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="false" replacement="!$Instance$.hasAnnotation($Parameter$)">
          <constraint name="Instance" nameOfExprType="com\\.intellij\\.psi\\.PsiAnnotationOwner" exprTypeWithinHierarchy="true" minCount="0" within="" contains="" />
          <constraint name="Parameter" minCount="0" maxCount="2147483647" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="incorrect DumbAware check" created="1522680840725" text="$target$ instanceof DumbAware" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.project.DumbService.isDumbAware($target$)">
          <constraint name="target" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="invalid use of ArrayUtil.contains: always returns false" text="com.intellij.util.ArrayUtil.contains($t$, $e$)" recursive="true" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="$e$.contains($t$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="t" within="" contains="" />
          <constraint name="e" nameOfExprType="java\\.util\\.Collection" exprTypeWithinHierarchy="true" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Logger.getInstance(unknown class)" text="Logger.getInstance(&quot;#$c$&quot;)" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.search.GlobalSearchScope&#10;import com.intellij.psi.util.PsiUtil&#10;&#10;def project = __context__.getProject();&#10;def fqn = c.getValue().substring(1);&#10;if (fqn.indexOf('.') == -1) return false;&#10;def shortName = com.intellij.openapi.util.text.StringUtil.getShortName(fqn);&#10;if (!shortName.equals(com.intellij.openapi.util.text.StringUtil.capitalize(shortName))) return false;&#10;&#10;def cClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));&#10; cClass == null&#10;&quot;" target="true" within="" contains="" />
          <constraint name="c" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="Logger expression can be simplified" uuid="0befec03-d2b3-3838-ada0-363adc05fac7" text="Logger.getInstance(&quot;#$c$&quot;)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="Logger.getInstance($c$.class)">
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.search.GlobalSearchScope&#10;import com.intellij.psi.util.PsiUtil&#10;&#10;def project = __context__.getProject();&#10;def cClass = JavaPsiFacade.getInstance(project).findClass(c.getValue().substring(1), GlobalSearchScope.allScope(project));&#10;def cVirtualFile = c==null?null:PsiUtil.getVirtualFile(cClass);&#10;&#10;cVirtualFile != null &amp;&amp;  &#10;__context__.getContainingFile().getResolveScope().contains(cVirtualFile)&#10;&quot;" target="true" within="" contains="" />
          <constraint name="c" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Logger expression can be simplified" uuid="0befec03-d2b3-3838-ada0-363adc05fac7" text="com.intellij.openapi.diagnostic.Logger.getInstance(&quot;#&quot; + $c$.class.getName())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.diagnostic.Logger.getInstance($c$.class)">
          <constraint name="c" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Logger expression can be simplified" uuid="0befec03-d2b3-3838-ada0-363adc05fac7" text="$LOG$.assertTrue(false, $e$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="$LOG$.error($e$)">
          <constraint name="e" within="" contains="" />
          <constraint name="LOG" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Manual TextRange.substring implementation" description="Reports code pattern like str.substring(range.getStartOffset(), range.getEndOffset()). This code could be replaced with range.substring(str)" problemDescriptor="TextRange.substring() could be used" text="$str$.substring($range$.getStartOffset(), $range$.getEndOffset())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="$range$.substring($str$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="str" nameOfExprType="java\\.lang\\.String" within="" contains="" />
          <constraint name="range" nameOfExprType="com\\.intellij\\.openapi\\.util\\.TextRange" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Method reference can slow down startup" description="Method references in class initializers cause qualifier class to be loaded as well, even if that reference would never be invoked" text="$C$::new" recursive="false" caseInsensitive="false" type="JAVA" pattern_context="default">
          <constraint name="__context__" script="&quot;import com.intellij.psi.*&#10;import com.intellij.psi.util.PsiTreeUtil &#10;&#10;def member = PsiTreeUtil.getParentOfType(__context__, PsiMember)&#10;if ((member instanceof PsiClassInitializer || member instanceof PsiField) &amp;&amp; member.hasModifierProperty(PsiModifier.STATIC)) {&#10;  def clazz = member.parent&#10;  def target = C.resolve()&#10;  def targetName = target?.qualifiedName&#10;  target != null &amp;&amp; clazz != target &amp;&amp; !target.qualifiedName.startsWith(&quot;java.&quot;)&#10;}&#10;else false&quot;" within="" contains="" />
          <constraint name="C" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="new String(byte[]) is default locale dependent; use new String(byte[], Charset) instead" text="new String($b$)" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="new String($b$, com.intellij.openapi.vfs.CharsetToolkit.UTF8_CHARSET)">
          <constraint name="b" nameOfExprType="byte\\[\\]" exprTypeWithinHierarchy="true" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="non-static JamAnnotationMeta" created="1523014327447" text="class $Class$ {&#10;  $Type$ $Variable$ = $Init$;&#10;}" recursive="false" caseInsensitive="true" type="JAVA">
          <constraint name="Class" within="" contains="" />
          <constraint name="Type" regexp="JamAnnotationMeta" within="" contains="" />
          <constraint name="Variable" script="&quot;!__context__.hasModifierProperty(&quot;static&quot;)&quot;" maxCount="2147483647" target="true" within="" contains="" />
          <constraint name="Init" within="" contains="" />
          <constraint name="__context__" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="notNullize(s,&quot;&quot;) can be simplified" text="com.intellij.openapi.util.text.StringUtil.notNullize($s$, &quot;&quot;)" recursive="true" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.util.text.StringUtil.notNullize($s$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="s" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="nullability for array elements specified, probably nullability for the array itself was intended" text="@$Anno$ $Type$ $x$;" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" within="" contains="" />
          <constraint name="x" within="" contains="" />
          <constraint name="Anno" regexp="Nullable|NotNull" target="true" within="" contains="" />
          <constraint name="Type" script="&quot;def parent = Type.parent;&#10;com.intellij.psi.util.PsiUtil.isLanguageLevel8OrHigher(Type) &amp;&amp;&#10;!parent.hasAnnotation(&quot;org.jetbrains.annotations.NotNull&quot;) &amp;&amp; &#10;!parent.hasAnnotation(&quot;org.jetbrains.annotations.Nullable&quot;);&quot;" regexp=".*(\\[\\])+" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="nullability for array elements specified, probably nullability for the array itself was intended" text="@$Anno$ $Type$ $x$($T$ $param$);" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="member">
          <constraint name="__context__" within="" contains="" />
          <constraint name="x" within="" contains="" />
          <constraint name="Anno" regexp="Nullable|NotNull" target="true" within="" contains="" />
          <constraint name="Type" script="&quot;def parent = Type.parent;&#10;com.intellij.psi.util.PsiUtil.isLanguageLevel8OrHigher(Type) &amp;&amp;&#10;!parent.hasAnnotation(&quot;org.jetbrains.annotations.NotNull&quot;) &amp;&amp; &#10;!parent.hasAnnotation(&quot;org.jetbrains.annotations.Nullable&quot;);&quot;" regexp=".*(\\[\\])+" within="" contains="" />
          <constraint name="T" within="" contains="" />
          <constraint name="param" minCount="0" maxCount="2147483647" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="Over-dependence on fastutil Collections" uuid="8c028576-2d38-32d6-a013-7c3ce0e29f09" description="Factory method CollectionFactory.createXXX() should be used to reduce dependence on concrete collection implementation from third-party library " problemDescriptor="Should use factory method instead of direct ctr call" text="new Object2ObjectOpenHashMap&lt;&gt;($a$)" recursive="false" caseInsensitive="false" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="com.intellij.util.containers.CollectionFactory.createSmallMemoryFootprintMap($a$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="a" minCount="0" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Over-dependence on fastutil Collections" uuid="8c028576-2d38-32d6-a013-7c3ce0e29f09" text="new Object2ObjectLinkedOpenHashMap&lt;&gt;($a$)" recursive="false" caseInsensitive="false" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="com.intellij.util.containers.CollectionFactory.createSmallMemoryFootprintLinkedMap($a$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="a" minCount="0" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Over-dependence on fastutil Collections" uuid="8c028576-2d38-32d6-a013-7c3ce0e29f09" text="new ObjectOpenHashSet&lt;&gt;($a$)" recursive="false" caseInsensitive="false" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="com.intellij.util.containers.CollectionFactory.createSmallMemoryFootprintSet($a$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="a" minCount="0" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Potential memory leak: don't hold PsiElement inside quick fix, use SmartPsiElementPointer or instead of; also see LocalQuickFixOnPsiElement" text="class $Class$ { &#10;  $FieldType$ $FieldName$ = $Init$;&#10;}" recursive="false" caseInsensitive="false" type="JAVA">
          <constraint name="Class" script="&quot;import com.intellij.psi.PsiClass&#10;import com.intellij.psi.util.InheritanceUtil&#10;import com.intellij.psi.util.PsiTreeUtil&#10;&#10;Object c = __context__&#10;return c instanceof PsiClass &amp;&amp; InheritanceUtil.isInheritor(c, &quot;com.intellij.codeInspection.LocalQuickFix&quot;) &#10;&quot;" within="" contains="" />
          <constraint name="FieldType" script="&quot;import com.intellij.psi.PsiClass&#10;import com.intellij.psi.PsiElement&#10;import com.intellij.psi.PsiField&#10;import com.intellij.psi.util.InheritanceUtil&#10;import com.intellij.psi.util.PsiTreeUtil&#10;&#10;PsiField f = PsiTreeUtil.getParentOfType(__context__, PsiField)&#10;return f != null &amp;&amp; InheritanceUtil.isInheritor(f.getType(), &quot;com.intellij.psi.PsiElement&quot;) &#10;&quot;" maxCount="2147483647" within="" contains="" />
          <constraint name="FieldName" maxCount="2147483647" target="true" within="" contains="" />
          <constraint name="Init" minCount="0" maxCount="2147483647" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="Potential non-desired escape from lambda expression" text="PsiTreeUtil.getParentOfType($Parameter$, PsiMethod.class, true, PsiClass.class)" recursive="false" caseInsensitive="false" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" useStaticImport="true" replacement="PsiTreeUtil.getParentOfType($Parameter$, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class)">
          <constraint name="Parameter" script="&quot;&quot;" minCount="0" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Presentation setEnabledAndVisible" text="$Var1$.setEnabled($Var2$);&#10;$Var1$.setVisible($Var2$);&#10;" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="false" shortenFQN="false" replacement="$Var1$.setEnabledAndVisible($Var2$);">
          <constraint name="__context__" target="true" within="" contains="" />
          <constraint name="Var2" within="" contains="" />
          <constraint name="Var1" nameOfExprType="Presentation" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Presentation setEnabledAndVisible" text="$Var1$.setVisible($Var2$);&#10;$Var1$.setEnabled($Var2$);&#10;" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="false" replacement="$Var1$.setEnabledAndVisible($Var2$);">
          <constraint name="__context__" target="true" within="" contains="" />
          <constraint name="Var2" within="" contains="" />
          <constraint name="Var1" nameOfExprType="Presentation" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="PsiManager.getInstance(psiElement.getProject())" text="com.intellij.psi.PsiManager.getInstance($psiElement$.getProject())" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="$psiElement$.getManager()">
          <constraint name="psiElement" script="&quot;&quot;" nameOfExprType="com\\.intellij\\.psi\\.PsiElement" exprTypeWithinHierarchy="true" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="remove test-only branch and use UIUtil.dispatchAllInvocationEvents in tests" text="if ($APP$.isUnitTestMode()) {&#10;  $DO$;&#10;} else {&#10;  $APP2$.$INV$($ARGS$);&#10;}" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" within="" contains="" />
          <constraint name="APP" within="" contains="" />
          <constraint name="APP2" within="" contains="" />
          <constraint name="INV" regexp="(invoke.*)|(.*Later.*)" within="" contains="" />
          <constraint name="ARGS" maxCount="2147483647" within="" contains="" />
          <constraint name="DO" maxCount="2147483647" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="remove test-only branch and use UIUtil.dispatchAllInvocationEvents in tests" text="if (!$APP$.isUnitTestMode()) {&#10;  $APP2$.$INV$($ARGS$);&#10;} else {&#10;  $DO$;&#10;}" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="false" replacement="if (!$APP$.isUnitTestMode()) {&#10;  $APP2$.$INV$($ARGS$);&#10;} else {&#10;  $DO$;&#10;}">
          <constraint name="__context__" within="" contains="" />
          <constraint name="APP" within="" contains="" />
          <constraint name="APP2" within="" contains="" />
          <constraint name="INV" regexp="(invoke.*)|(.*Later.*)" within="" contains="" />
          <constraint name="ARGS" maxCount="2147483647" within="" contains="" />
          <constraint name="DO" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Replace explicit RExpression#getType with RTypeUtil#getType" text="((RExpression)$foo$).getType()" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="org.jetbrains.plugins.ruby.ruby.codeInsight.types.RTypeUtil.getType($foo$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="foo" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="searchable configurable should not contain JComponent fields" text="class $Class$ implements SearchableConfigurable{ &#10;  @Modifier(&quot;Instance&quot;) $FieldType$ $FieldName$ = $Init$;&#10;  public void disposeUIResources(){}&#10;}" recursive="false" caseInsensitive="false" type="JAVA">
          <constraint name="Class" script="&quot;&quot;" target="true" within="" contains="" />
          <constraint name="FieldType" script="&quot;&quot;" regexp="javax.swing.JComponent" withinHierarchy="true" formalTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="FieldName" script="&quot;&quot;" maxCount="2147483647" within="" contains="" />
          <constraint name="Init" script="&quot;&quot;" minCount="0" maxCount="2147483647" within="" contains="" />
          <constraint name="__context__" script="&quot;&quot;" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Storing localized strings in static fields prevents dynamic loading of language bundles" text="$bundle$.$message$(&quot;$s$&quot;, $p$)" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" script="&quot;import com.intellij.psi.*;&#10;import com.intellij.psi.util.*;&#10;PsiElement member = PsiTreeUtil.getParentOfType(__context__, PsiMember.class, PsiLambdaExpression.class)&#10;return member instanceof PsiField &amp;&amp; member.hasModifierProperty(PsiModifier.STATIC)&quot;" within="" contains="" />
          <constraint name="s" within="" contains="" />
          <constraint name="bundle" regexp="AbstractBundle" withinHierarchy="true" within="" contains="" />
          <constraint name="p" minCount="0" maxCount="2147483647" within="" contains="" />
          <constraint name="message" regexp="message" target="true" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" description="stream statements create Stream instance while equivalent ContainerUtil methods aren't" problemDescriptor="can be replaced with ContainerUtil" text="Arrays.stream($m$).allMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.and($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="Arrays.stream($m$).anyMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.exists($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="Arrays.stream($m$).noneMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="!com.intellij.util.containers.ContainerUtil.exists($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="Stream.of($m$).allMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.and($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="Stream.of($m$).anyMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.or($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="Stream.of($m$).noneMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="!com.intellij.util.containers.ContainerUtil.exists($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="StreamEx.of($m$).allMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.and($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="StreamEx.of($m$).anyMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.or($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="StreamEx.of($m$).noneMatch($p$)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="!com.intellij.util.containers.ContainerUtil.exists($m$, $p$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="m" within="" contains="" />
          <constraint name="p" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="$f$.stream().map($m$).collect(Collectors.toList())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.map($f$, $m$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="f" within="" contains="" />
          <constraint name="m" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="Arrays.stream($f$).map($m$).collect(Collectors.toList())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.map($f$, $m$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="f" within="" contains="" />
          <constraint name="m" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="Stream.of($f$).map($m$).collect(Collectors.toList())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.map($f$, $m$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="f" within="" contains="" />
          <constraint name="m" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="StreamEx.of($f$).map($m$).collect(Collectors.toList())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.map($f$, $m$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="f" within="" contains="" />
          <constraint name="m" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="$l$.stream().filter($f$).collect(Collectors.toList())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.filter($l$, $f$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="l" within="" contains="" />
          <constraint name="f" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="Arrays.stream($l$).filter($f$).collect(Collectors.toList())" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.filter($l$, $f$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="l" within="" contains="" />
          <constraint name="f" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="$l$.stream().filter($f$).findAny().orElse(null)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.find($l$, $f$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="l" within="" contains="" />
          <constraint name="f" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="$l$.stream().filter($f$).findFirst().orElse(null)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.find($l$, $f$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="l" within="" contains="" />
          <constraint name="f" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="Arrays.stream($l$).filter($f$).findAny().orElse(null)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.find($l$, $f$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="l" within="" contains="" />
          <constraint name="f" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="stream statements can be replaced with ContainerUtil" text="Arrays.stream($l$).filter($f$).findFirst().orElse(null)" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.util.containers.ContainerUtil.find($l$, $f$)">
          <constraint name="__context__" within="" contains="" />
          <constraint name="l" within="" contains="" />
          <constraint name="f" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="String.getBytes() is current locale-dependant, use String.getBytes(Charset) instead" text="$s$.getBytes()" recursive="false" caseInsensitive="true" type="JAVA">
          <constraint name="s" script="&quot;&quot;" nameOfExprType="java.lang.String" exprTypeWithinHierarchy="true" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="StringUtil.indexOf(String, char) -&gt; String.indexOf(char)" text="com.intellij.openapi.util.text.StringUtil.indexOf($s$, $c$)" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="false" shortenFQN="false" replacement="$s$.indexOf($c$)">
          <constraint name="s" nameOfExprType="java.lang.String" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="c" nameOfExprType="char" exprTypeWithinHierarchy="true" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="StringUtil.stripQuotesAroundValue" text="com.intellij.openapi.util.text.StringUtil.stripQuotesAroundValue($Parameter$)" recursive="false" caseInsensitive="false" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.openapi.util.text.StringUtil.unquoteString($Parameter$)">
          <constraint name="Parameter" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="submit() creates unnecessary Future" text="$e$.submit($d$);" recursive="false" caseInsensitive="true" type="JAVA" pattern_context="default" reformatAccordingToStyle="false" shortenFQN="true" replacement="$e$.execute($d$);">
          <constraint name="__context__" within="" contains="" />
          <constraint name="e" nameOfExprType="java\\.util\\.concurrent\\.ExecutorService" exprTypeWithinHierarchy="true" within="" contains="" />
          <constraint name="d" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="super.update() in AnAction subclass" text="$super$.update($e$);" recursive="true" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="false" shortenFQN="false" replacement="">
          <constraint name="__context__" target="true" within="" contains="" />
          <constraint name="super" regexp="super" nameOfExprType="AnAction" within="" contains="" />
          <constraint name="e" nameOfExprType="AnActionEvent" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="Suppressed exceptions are ignored" text="try {&#10;  $TryStatement$;&#10;} &#10;finally {&#10;  $s1$;&#10;  super.tearDown();&#10;  $s2$;&#10;}" recursive="true" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="try {&#10;  $TryStatement$;&#10;} &#10;catch (Throwable e) {&#10;  addSuppressedException(e);&#10;}&#10;finally {&#10;  $s1$; super.tearDown(); $s2$;&#10;}">
          <constraint name="__context__" script="com.intellij.psi.PsiTryStatement ts = com.intellij.psi.util.PsiTreeUtil.getParentOfType(__context__, com.intellij.psi.PsiTryStatement.class, false); return ts != null &amp;&amp; ts.getCatchSections().length == 0" within="" contains="" />
          <constraint name="TryStatement" maxCount="2147483647" within="" contains="" />
          <constraint name="s1" minCount="0" maxCount="2147483647" within="" contains="" />
          <constraint name="s2" minCount="0" maxCount="2147483647" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="Suspicious usage of PsiUtilBase#findEditor inside quick fix, probably better idea to use LocalQuickFixAndIntentionActionOnPsiElement" text="$Instance$.$MethodCall$($Parameter$)" recursive="false" caseInsensitive="false" type="JAVA">
          <constraint name="Instance" script="&quot;&quot;" regexp="com.intellij.psi.util.PsiUtilBase" minCount="0" within="" contains="" />
          <constraint name="MethodCall" script="&quot;import com.intellij.psi.PsiClass&#10;import com.intellij.psi.util.InheritanceUtil&#10;import com.intellij.psi.util.PsiTreeUtil&#10;&#10;PsiClass aClass = PsiTreeUtil.getParentOfType(__context__, PsiClass.class)&#10;aClass != null &amp;&amp; InheritanceUtil.isInheritor(aClass, &quot;com.intellij.codeInspection.LocalQuickFix&quot;)&quot;" regexp="findEditor" target="true" within="" contains="" />
          <constraint name="Parameter" script="&quot;&quot;" minCount="0" maxCount="2147483647" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="SwingWorker generates too many threads; use Application.execute*() instead" text="javax.swing.SwingWorker" recursive="true" caseInsensitive="true" type="JAVA" pattern_context="default">
          <constraint name="__context__" within="" contains="" />
        </searchConfiguration>
        <replaceConfiguration name="TokenSet.create(TokenType.WHITE_SPACE) -&gt; TokenSet.WHITE_SPACE" text="com.intellij.psi.tree.TokenSet.create(com.intellij.psi.TokenType.WHITE_SPACE)" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="com.intellij.psi.tree.TokenSet.WHITE_SPACE" />
        <replaceConfiguration name="Too convoluted &quot;register(()-&gt;dispose())&quot; (wastes memory)" text="Disposer.register($myRoot$, () -&gt; Disposer.dispose($myFolder2$));" recursive="true" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="true" shortenFQN="true" replacement="Disposer.register($myRoot$, $myFolder2$);">
          <constraint name="__context__" within="" contains="" />
          <constraint name="myRoot" within="" contains="" />
          <constraint name="myFolder2" within="" contains="" />
        </replaceConfiguration>
        <replaceConfiguration name="use explicit toArray() method" text="com.intellij.util.ArrayUtil.toObjectArray($collection$, $class$.class)" recursive="false" caseInsensitive="true" type="JAVA" reformatAccordingToStyle="false" shortenFQN="false" replacement="$collection$.toArray(new $class$[0])">
          <constraint name="__context__" within="" contains="" />
          <constraint name="class" within="" contains="" />
          <constraint name="collection" nameOfExprType="java\\.util\\.Collection" exprTypeWithinHierarchy="true" within="" contains="" />
        </replaceConfiguration>
        <searchConfiguration name="use NotificationGroup.toolWindowGroup().createNotification().notify() instead" text="$Instance$.notifyByBalloon($Parameter$)" recursive="false" caseInsensitive="false" type="JAVA">
          <constraint name="Instance" script="&quot;&quot;" nameOfExprType="com.intellij.openapi.wm.ToolWindowManager" exprTypeWithinHierarchy="true" minCount="0" within="" contains="" />
          <constraint name="Parameter" minCount="0" maxCount="2147483647" within="" contains="" />
        </searchConfiguration>
        <searchConfiguration name="Use RecursionManager?" text="class $Class$ { &#10;  ThreadLocal&lt;$FieldType$&gt; $FieldName$ = $Init$;&#10;}" recursive="false" caseInsensitive="false" type="JAVA">
          <constraint name="Class" script="&quot;&quot;" within="" contains="" />
          <constraint name="FieldType" script="&quot;&quot;" regexp="Collection" withinHierarchy="true" maxCount="2147483647" target="true" wholeWordsOnly="true" within="" contains="" />
          <constraint name="FieldName" script="&quot;&quot;" maxCount="2147483647" within="" contains="" />
          <constraint name="Init" script="&quot;&quot;" minCount="0" maxCount="2147483647" within="" contains="" />
          <constraint name="__context__" script="&quot;&quot;" within="" contains="" />
        </searchConfiguration>
      </inspection_tool>""";

  public void testOldSettingsMigrated() throws Exception {
    assertEquals(NEW_SETTINGS, writeSettings(readSettings(OLD_SETTINGS)));
  }

  public void testOldSettingsModified() throws Exception {
    final SSBasedInspection inspection = readSettings(OLD_SETTINGS);
    final List<Configuration> configurations = inspection.getConfigurations();
    final int last = configurations.size() - 1;
    inspection.removeConfiguration(configurations.get(last)); // change
    inspection.addConfiguration(configurations.get(last)); // revert
    inspection.removeConfigurationsWithUuid("00000000-0000-0000-0000-000000000000"); // no change
    assertEquals(NEW_SETTINGS, writeSettings(inspection));
  }

  public void testNewSettingsNotModified() throws Exception {
    // check if order is set correctly after loading
    final SSBasedInspection inspection = readSettings(NEW_SETTINGS);
    Configuration previous = null;
    for (Configuration configuration : inspection.getConfigurations()) {
      final boolean family = previous != null && previous.getUuid().equals(configuration.getUuid());
      assertEquals(family ? previous.getOrder() + 1 : 0, configuration.getOrder());
      previous = configuration;
    }

    assertEquals(NEW_SETTINGS, writeSettings(inspection));
  }

  public void testScopedInspectionDoesNotDisappearOnWrite() throws Exception {
    var initInspections = InspectionProfileImpl.INIT_INSPECTIONS;
    if (!initInspections) {
      InspectionProfileImpl.INIT_INSPECTIONS = true;
      Disposer.register(getTestRootDisposable(), () -> InspectionProfileImpl.INIT_INSPECTIONS = initInspections);
    }

    var profileXml = """
      <profile version="1.0">
        <option name="myName" value="foo.profile" />
        <inspection_tool class="SSBasedInspection" enabled="true" level="WARNING" enabled_by_default="true">
          <searchConfiguration name="foo.search" text="..." recursive="false" caseInsensitive="false" type="JAVA" />
        </inspection_tool>
        <inspection_tool class="e3a3ba52-b4e2-3010-a5de-4e1ff9d4f37b" enabled="true" level="WARNING" enabled_by_default="true">
          <scope name="Tests" level="WARNING" enabled="true" />
        </inspection_tool>
      </profile>""";
    var wrapper = new LocalInspectionToolWrapper(new SSBasedInspection());
    var supplier = new InspectionToolsSupplier.Simple(List.of(wrapper));
    var profile = new InspectionProfileImpl("foo.profile", supplier, InspectionProfileImpl.BASE_PROFILE.get());
    profile.readExternal(JDOMUtil.load(profileXml));
    profile.initInspectionTools(getProject());
    profile.profileChanged();
    assertEquals(profileXml, JDOMUtil.write(profile.writeScheme()));
  }

  private static String writeSettings(SSBasedInspection inspection) {
    final Element out = new Element("inspection_tool");
    inspection.writeSettings(out);
    return JDOMUtil.writeElement(out);
  }

  private static SSBasedInspection readSettings(String xml) throws IOException, JDOMException {
    final Element in = JDOMUtil.load(xml);
    final SSBasedInspection inspection = new SSBasedInspection();
    inspection.readSettings(in);
    return inspection;
  }
}
