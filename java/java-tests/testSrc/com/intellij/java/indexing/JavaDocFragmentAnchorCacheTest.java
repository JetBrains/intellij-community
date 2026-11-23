// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.indexing;

import com.intellij.codeInsight.javadoc.JavaDocFragmentAnchorCacheKt;
import com.intellij.codeInsight.javadoc.JavaDocFragmentData;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.javadoc.PsiDocFragmentName;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.intellij.lang.annotations.Language;

import java.util.ArrayList;
import java.util.Collection;

public class JavaDocFragmentAnchorCacheTest extends LightJavaCodeInsightTestCase {

  public void testIndexes() {
    @Language("JAVA") String text = """
      package p;
      /**
       * <h1>Title with derived heading</h1>
       * <h2 id=equivalenceRelation>Equivalence</h2>
       * <a id='closing'></a>
       * <p id="my-id"></a>
       * {@systemProperty hihi.haha}
       * {@index classIndex description about the class index}
       */
      public class A {
        /**
         * <h1 id="myConst"></h1>
         * {@systemProperty myconst}
         * {@index "field index" description about the field index}
         */
        public static final int MY_CONST = 0;
      
        /**
         * <p id=fooMethod></p>\s
         * {@systemProperty foo.bar}
         * {@index methodIndex A method index}
         * {@index "quoted method index" A quoted method index}
         */
        void foo() { }
      
        /// ## Very nice title with derived heading
        /// ### I also have a derived heading
        void bar() {}
      }
      """;
    configureFromFileText("A.java", text);

    Project project = getProject();

    PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass("p.A", GlobalSearchScope.projectScope(project));
    assertNotNull(psiClass);

    Collection<JavaDocFragmentData> anchors = JavaDocFragmentAnchorCacheKt.getJavaDocFragmentsForClass(project, psiClass);
    assertNotNull(anchors);
    assertContainsElements(anchors,
                           new JavaDocFragmentData("title-with-derived-heading-heading", 22),
                           new JavaDocFragmentData("equivalenceRelation", 64),
                           new JavaDocFragmentData("closing", 111),
                           new JavaDocFragmentData("my-id", 135),
                           new JavaDocFragmentData("hihi.haha", 150),
                           new JavaDocFragmentData("classIndex", 181),
                           new JavaDocFragmentData("fooMethod", 447),
                           new JavaDocFragmentData("foo.bar", 468),
                           new JavaDocFragmentData("methodIndex", 499),
                           new JavaDocFragmentData("quotedmethodindex", 540),
                           new JavaDocFragmentData("very-nice-title-with-derived-heading-heading", 626),
                           new JavaDocFragmentData("i-also-have-a-derived-heading-heading", 673),
                           new JavaDocFragmentData("myConst", 275),
                           new JavaDocFragmentData("myconst", 295),
                           new JavaDocFragmentData("fieldindex", 326)
    );
  }


  public void testReferencesToPackageInfo() {
    @Language("JAVA") String text = """
      /**
       * <h2 id="package-id">Package</h2>
       * {@systemProperty package.property}
       * {@index packageIndex Package index}
       * {@index "quoted package index" Package index}
       * {@link p##package-id Package section}
       * {@link p##package.property Package property}
       * {@link p##packageIndex Package index}
       * {@link p##quotedpackageindex Quoted package index}
       */
      package p;
      """;
    configureFromFileText("p/package-info.java", text);

    var fragments = PsiTreeUtil.findChildrenOfType(getFile(), PsiDocFragmentName.class);
    assertSize(4, fragments);
    var anchors = new ArrayList<JavaDocFragmentData>();
    for (var fragment : fragments) {
      var resolved = JavaDocFragmentAnchorCacheKt.resolveJavaDocFragment(getProject(), fragment);
      assertNotNull(fragment.getText(), resolved);
      assertSame(getFile(), resolved.getFirst().getContainingFile());
      anchors.add(resolved.getSecond());
    }
    assertSameElements(anchors,
                       new JavaDocFragmentData("package-id", text.indexOf("package-id")),
                       new JavaDocFragmentData("package.property", text.indexOf("{@systemProperty")),
                       new JavaDocFragmentData("packageIndex", text.indexOf("{@index packageIndex")),
                       new JavaDocFragmentData("quotedpackageindex", text.indexOf("{@index \"quoted package index\"")));
  }
}
