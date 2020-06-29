// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi

import com.intellij.codeInspection.defaultFileTemplateUsage.DefaultFileTemplateUsageInspection
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.java.JavaFileElement
import com.intellij.psi.impl.source.tree.java.MethodElement
import com.intellij.psi.impl.source.tree.java.ParameterElement
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ref.GCWatcher

import java.util.function.Predicate
/**
 * @author peter
 */
class AstLeaksTest extends LightJavaCodeInsightFixtureTestCase {

  void "test AST should be on a soft reference, for changed files as well"() {
    def file = myFixture.addClass('class Foo {}').containingFile
    assert file.findElementAt(0) instanceof PsiKeyword
    LeakHunter.checkLeak(file, JavaFileElement) { e -> e.psi == file }

    WriteCommandAction.runWriteCommandAction project, {
      file.viewProvider.document.insertString(0, ' ')
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    assert file.findElementAt(0) instanceof PsiWhiteSpace
    LeakHunter.checkLeak(file, JavaFileElement) { e -> e.psi == file }
  }

  void "test super methods held via their signatures in class user data"() {
    def superClass = myFixture.addClass('class Super { void foo() {} }')
    superClass.text // load AST

    def file = myFixture.addFileToProject('Main.java', 'class Main extends Super { void foo() { System.out.println("hello"); } }')
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.doHighlighting()

    def mainClass = ((PsiJavaFile)file).classes[0]
    LeakHunter.checkLeak(mainClass, MethodElement, { MethodElement node ->
      superClass == node.psi.parent
    } as Predicate<MethodElement>)
  }

  void "test no hard refs to AST after highlighting"() {
    def sup = myFixture.addFileToProject('sup.java', 'class Super { Super() {} }')
    assert sup.findElementAt(0) // load AST
    assert !((PsiFileImpl)sup).stub

    LeakHunter.checkLeak(sup, MethodElement, { it.psi.containingFile == sup } as Predicate)

    def foo = myFixture.addFileToProject('a.java', 'class Foo extends Super { void bar() { bar(); } }')
    myFixture.configureFromExistingVirtualFile(foo.virtualFile)
    myFixture.doHighlighting()

    assert !((PsiFileImpl)foo).stub
    assert ((PsiFileImpl)foo).treeElement

    LeakHunter.checkLeak(foo, MethodElement, { it.psi.containingFile == foo } as Predicate)
    LeakHunter.checkLeak(sup, MethodElement, { it.psi.containingFile == sup } as Predicate)
  }

  void "test no hard refs to Default File Template inspection internal AST"() {
    myFixture.addFileToProject('sup.java', 'class Super { void bar() {} }')
    def file = myFixture.addFileToProject('a.java', 'class Foo { void bar() { bar(); } }')
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.enableInspections(new DefaultFileTemplateUsageInspection())
    myFixture.doHighlighting()

    def mainClass = ((PsiJavaFile)file).classes[0]
    LeakHunter.checkLeak(mainClass, MethodElement, { MethodElement node ->
      !node.psi.physical
    } as Predicate<MethodElement>)
  }

  void "test no hard refs to AST via class reference type"() {
    def cls = myFixture.addClass("class Foo { Object bar() {} }")
    def file = cls.containingFile as PsiFileImpl
    cls.node
    def type = cls.methods[0].returnType
    assert type instanceof PsiClassReferenceType

    LeakHunter.checkLeak(type, MethodElement, { MethodElement node ->
      node.psi == cls.methods[0]
    } as Predicate<MethodElement>)

    GCWatcher.tracking(cls.node).ensureCollected()
    assert !file.contentsLoaded

    assert type.equalsToText(Object.name)
    assert !file.contentsLoaded
  }

  @SuppressWarnings('CStyleArrayDeclaration')
  void "test no hard refs to AST via class reference type of c-style array"() {
    def cls = myFixture.addClass("class Foo { static void main(String args[]) {} }")
    def file = cls.containingFile as PsiFileImpl
    cls.node
    def type = cls.methods[0].parameterList.parameters[0].typeElement.type
    assert type instanceof PsiClassReferenceType

    LeakHunter.checkLeak(type, ParameterElement, { ParameterElement node ->
      node.psi == cls.methods[0].parameterList.parameters[0]
    } as Predicate<ParameterElement>)

    GCWatcher.tracking(cls.node).ensureCollected()
    assert !file.contentsLoaded

    assert type.equalsToText(String.name)
    assert !file.contentsLoaded
  }

  void "test no hard refs to AST via array component type"() {
    def cls = myFixture.addClass("class Foo { Object[] bar() {} }")
    def file = cls.containingFile as PsiFileImpl
    cls.node
    def type = cls.methods[0].returnType
    assert type instanceof PsiArrayType
    def componentType = type.getComponentType()
    assert componentType instanceof PsiClassReferenceType

    LeakHunter.checkLeak(type, MethodElement, { MethodElement node ->
      node.psi == cls.methods[0]
    } as Predicate<MethodElement>)

    GCWatcher.tracking(cls.node).ensureCollected()
    assert !file.contentsLoaded

    assert componentType.equalsToText(Object.name)
    assert !file.contentsLoaded
  }

  void "test no hard refs to AST via generic component type"() {
    def cls = myFixture.addClass("class Foo { java.util.Map<String[], ? extends CharSequence> bar() {} }")
    def file = cls.containingFile as PsiFileImpl
    cls.node
    def type = cls.methods[0].returnType
    assert type instanceof PsiClassReferenceType
    def parameters = (type as PsiClassReferenceType).getParameters()
    assert parameters.length == 2
    assert parameters[0] instanceof PsiArrayType
    def componentType = parameters[0].getDeepComponentType()
    assert componentType instanceof PsiClassReferenceType
    assert parameters[1] instanceof PsiWildcardType
    def bound = (parameters[1] as PsiWildcardType).getExtendsBound()
    assert bound instanceof PsiClassReferenceType

    LeakHunter.checkLeak(type, MethodElement, { MethodElement node ->
      node.psi == cls.methods[0]
    } as Predicate<MethodElement>)

    GCWatcher.tracking(cls.node).ensureCollected()
    assert !file.contentsLoaded

    assert componentType.equalsToText(String.name)
    assert bound.equalsToText(CharSequence.name)
    assert !file.contentsLoaded
  }

}
