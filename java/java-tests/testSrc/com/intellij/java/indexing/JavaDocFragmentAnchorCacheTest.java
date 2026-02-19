// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.indexing;

import com.intellij.codeInsight.javadoc.JavaDocFragmentAnchorCacheKt;
import com.intellij.codeInsight.javadoc.JavaDocFragmentData;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.intellij.lang.annotations.Language;

import java.util.Collection;

public class JavaDocFragmentAnchorCacheTest extends LightJavaCodeInsightTestCase {

  public void testIndexesExplicitId() {
    @Language("JAVA") String text = """
      package p;
      /**
       * <h2 id=equivalenceRelation>Equivalence</h2>
       * <a id='closing'></a>
       * <p id="my-id"></a>
       */
      public class A {
        /**
         * <h1 id="myConst"></h1>
         */
        public static final int MY_CONST = 0;
      
        /**
         * <p id=fooMethod></p>\s
         */
        void foo() { }
      }
      """;
    configureFromFileText("A.java", text);

    Project project = getProject();

    PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass("p.A", GlobalSearchScope.projectScope(project));
    assertNotNull(psiClass);

    Collection<JavaDocFragmentData> anchors = JavaDocFragmentAnchorCacheKt.getJavaDocFragmentsForClass(project, psiClass);
    assertNotNull(anchors);
    assertContainsElements(anchors,
                           new JavaDocFragmentData("equivalenceRelation", 25),
                           new JavaDocFragmentData("closing", 72),
                           new JavaDocFragmentData("my-id", 96),
                           new JavaDocFragmentData("myConst", 148),
                           new JavaDocFragmentData("fooMethod", 227)
    );
  }
}
