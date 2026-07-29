// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.search;

import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;
import java.util.Set;

/**
 * Tests for the fully-qualified-name based flavor of {@link AnnotatedElementsSearch} (and {@link MetaAnnotationUtil#getChildren}),
 * which matches annotations by name rather than by resolving them to a particular {@link PsiClass}. This makes results deterministic
 * even when the same annotation is present on the classpath more than once, and lets the search find usages whose annotation type
 * is not resolvable in the project at all.
 */
public class AnnotatedElementsSearchByNameTest extends JavaCodeInsightFixtureTestCase {

  public void testSearchByFqnFindsAnnotatedClass() {
    myFixture.addClass("package test; public @interface MyAnnotation {}");
    myFixture.addClass("package com.example; @test.MyAnnotation public class Foo {}");

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<PsiClass> result = AnnotatedElementsSearch.searchPsiClasses(getProject(), "test.MyAnnotation", scope).findAll();
    assertEquals(Set.of("com.example.Foo"), ContainerUtil.map2Set(result, PsiClass::getQualifiedName));
  }

  public void testSearchByFqnFindsUsageWithUnresolvableAnnotationType() {
    // The annotation type test.MyAnnotation is intentionally not declared: name-based search must still find the usage.
    PsiClass foo = myFixture.addClass("package com.example; @test.MyAnnotation public class Foo {}");
    myFixture.allowTreeAccessForFile(foo.getContainingFile().getVirtualFile());

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<PsiClass> result = AnnotatedElementsSearch.searchPsiClasses(getProject(), "test.MyAnnotation", scope).findAll();
    assertEquals(Set.of("com.example.Foo"), ContainerUtil.map2Set(result, PsiClass::getQualifiedName));
  }

  public void testSearchByFqnDistinguishesAnnotationsWithSameShortName() {
    myFixture.addClass("package a; public @interface MyAnnotation {}");
    myFixture.addClass("package b; public @interface MyAnnotation {}");
    myFixture.addClass("package com.example; @a.MyAnnotation public class WithA {}");
    myFixture.addClass("package com.example; @b.MyAnnotation public class WithB {}");

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Collection<PsiClass> result = AnnotatedElementsSearch.searchPsiClasses(getProject(), "a.MyAnnotation", scope).findAll();
    assertEquals(Set.of("com.example.WithA"), ContainerUtil.map2Set(result, PsiClass::getQualifiedName));
  }

  public void testGetChildrenByFqnReturnsOnlyAnnotationTypes() {
    myFixture.addClass("package meta; public @interface Meta {}");
    myFixture.addClass("package child; @meta.Meta public @interface ChildAnnotation {}");
    myFixture.addClass("package child; @meta.Meta public class NotAnAnnotation {}");

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    Set<PsiClass> children = MetaAnnotationUtil.getChildren(getProject(), "meta.Meta", scope);
    assertEquals(Set.of("child.ChildAnnotation"), ContainerUtil.map2Set(children, PsiClass::getQualifiedName));
  }
}
