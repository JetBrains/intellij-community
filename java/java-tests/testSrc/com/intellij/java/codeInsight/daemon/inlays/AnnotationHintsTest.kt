// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.AnnotationInlayProvider
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import org.intellij.lang.annotations.Language

class AnnotationHintsTest : InlayHintsProviderTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_21
  }

  fun `test contract inferred annotation`() {
    val text = """
class Demo {
/*<# block [[@ Contract [( [[pure  =  true]] )]]] #>*/
  private static int pure(int x, int y) {
    return x * y + 10;
  }
}"""
    testAnnotations(text)
  }

  fun `test arrays`() {
    val text = """
      final class Demo {
      /*<# block [[@ Contract [( [[value  =  "_ -> new"] ,  [pure  =  true]] )]]] #>*/
        String/*<# [[@ NotNull]] #>*/[][] data(String/*<# [[@ NotNull]] #>*/... arr) {
          if (arr.length == 0) return new String[10][20];
          return new String[20][30];
        }
      }
    """.trimIndent()
    testAnnotations(text)
  }

  fun `test arrays java7`() {
    val text = """
      final class Demo {
      /*<# block [[@ Contract [( [[value  =  "_ -> new"] ,  [pure  =  true]] )]] [@ NotNull]] #>*/
        String[][] data(/*<# [[@ NotNull]] #>*/String... arr) {
          if (arr.length == 0) return new String[10][20];
          return new String[20][30];
        }
      }
    """.trimIndent()
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_7) {
      testAnnotations(text)
    }
  }

  fun `test contract nullable`() {
    val text = """
public class E {
/*<# block [[@ Contract [( ["null -> true"] )]]] #>*/
  static boolean foo(E e) {
    if (e != null) {
      e.foo(new E());
    } else {
      return true;
    }
  }
}"""
    testAnnotations(text)
  }

  fun `test no parameters have no parens`() {
    val text = """
public class E {
/*<# block [[@ Contract [( [[pure  =  true]] )]]] #>*/
  static /*<# [[@ Nullable]] #>*/Boolean foo(E e) {
    if (true) return false;
    return null;
  }
}"""
    testAnnotations(text)
  }

  fun `test parameters annotations on the same line`() {
    val text = """
public class E {
  void foo(
      /*<# [[@ NotNull]] #>*/String s
    ) {
    s.length();  
  }
}"""
    testAnnotations(text)
  }

  fun `test external annotations`() {
    val optionalClass = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_UTIL_OPTIONAL, GlobalSearchScope.allScope(project))!!
    val file = optionalClass.containingFile
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val textWithHints = dumpInlayHints(file.text, AnnotationInlayProvider())
    assertEquals("""//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package java.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import jdk.internal.ValueBased;

@ValueBased
public final class Optional<T> {
    private static final Optional<?> EMPTY = new Optional((Object)null);
    private final T value;

    /*<# [[@ Contract [( [[pure  =  true]] )]]] #>*/public static <T> /*<# [[@ NotNull]] #>*/Optional<T> empty() {
        Optional<T> t = EMPTY;
        return t;
    }

    private Optional(T value) {
        this.value = value;
    }

    public static <T> /*<# [[@ NotNull]] #>*/Optional<T> of(/*<# [[@ Flow [( [[targetIsContainer  =  true]] )]]] #>*/T value) {
        return new Optional<T>(Objects.requireNonNull(value));
    }

    /*<# [[@ Contract [( [[pure  =  true]] )]]] #>*/public static <T> /*<# [[@ NotNull]] #>*/Optional<T> ofNullable(/*<# [[@ Flow [( [[targetIsContainer  =  true]] )]]] #>*/T value) {
        return value == null ? EMPTY : new Optional(value);
    }

    /*<# [[@ Contract [( [[pure  =  true]] )]] [@ Flow [( [[sourceIsContainer  =  true]] )]]] #>*/public /*<# [[@ NotNull]] #>*/T get() {
        if (this.value == null) {
            throw new NoSuchElementException("No value present");
        } else {
            return this.value;
        }
    }

    public boolean isPresent() {
        return this.value != null;
    }

    public boolean isEmpty() {
        return this.value == null;
    }

    public void ifPresent(/*<# [[@ NotNull]] #>*/Consumer<? super /*<# [[@ NotNull]] #>*/T> action) {
        if (this.value != null) {
            action.accept(this.value);
        }

    }

    public void ifPresentOrElse(/*<# [[@ NotNull]] #>*/Consumer<? super /*<# [[@ NotNull]] #>*/T> action, /*<# [[@ NotNull]] #>*/Runnable emptyAction) {
        if (this.value != null) {
            action.accept(this.value);
        } else {
            emptyAction.run();
        }

    }

    public Optional<T> filter(/*<# [[@ NotNull]] #>*/Predicate<? super /*<# [[@ NotNull]] #>*/T> predicate) {
        Objects.requireNonNull(predicate);
        if (this.isEmpty()) {
            return this;
        } else {
            return predicate.test(this.value) ? this : empty();
        }
    }

    public <U> Optional<U> map(/*<# [[@ NotNull]] #>*/Function<? super /*<# [[@ NotNull]] #>*/T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        return this.isEmpty() ? empty() : ofNullable(mapper.apply(this.value));
    }

    public <U> Optional<U> flatMap(/*<# [[@ NotNull]] #>*/Function<? super /*<# [[@ NotNull]] #>*/T, ? extends /*<# [[@ NotNull]] #>*/Optional<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        if (this.isEmpty()) {
            return empty();
        } else {
            Optional<U> r = (Optional)mapper.apply(this.value);
            return (Optional)Objects.requireNonNull(r);
        }
    }

    public /*<# [[@ NotNull]] #>*/Optional<T> or(/*<# [[@ NotNull]] #>*/Supplier<? extends /*<# [[@ NotNull]] #>*/Optional<? extends T>> supplier) {
        Objects.requireNonNull(supplier);
        if (this.isPresent()) {
            return this;
        } else {
            Optional<T> r = (Optional)supplier.get();
            return (Optional)Objects.requireNonNull(r);
        }
    }

    /*<# [[@ Contract [( [[pure  =  true]] )]]] #>*/public /*<# [[@ NotNull]] #>*/Stream<T> stream() {
        return this.isEmpty() ? Stream.empty() : Stream.of(this.value);
    }

    /*<# [[@ Contract [( [[value  =  "!null -> !null"] ,  [pure  =  true]] )]] [@ Flow [( [[sourceIsContainer  =  true]] )]]] #>*/public T orElse(/*<# [[[@ Flow [( [[targetIsContainer  =  true]] )]]] [[@ Nullable]]] #>*/T other) {
        return (T)(this.value != null ? this.value : other);
    }

    public T orElseGet(Supplier<? extends T> supplier) {
        return (T)(this.value != null ? this.value : supplier.get());
    }

    public T orElseThrow() {
        if (this.value == null) {
            throw new NoSuchElementException("No value present");
        } else {
            return this.value;
        }
    }

    public <X extends Throwable> T orElseThrow(/*<# [[@ NotNull]] #>*/Supplier<? extends /*<# [[@ NotNull]] #>*/X> exceptionSupplier) throws X {
        if (this.value != null) {
            return this.value;
        } else {
            throw (Throwable)exceptionSupplier.get();
        }
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else {
            boolean var10000;
            if (obj instanceof Optional) {
                Optional<?> other = (Optional)obj;
                if (Objects.equals(this.value, other.value)) {
                    var10000 = true;
                    return var10000;
                }
            }

            var10000 = false;
            return var10000;
        }
    }

    public int hashCode() {
        return Objects.hashCode(this.value);
    }

    public String toString() {
        return this.value != null ? "Optional[" + this.value + "]" : "Optional.empty";
    }
}
""", textWithHints)
  }

  private fun testAnnotations(
    @Language("JAVA") text: String,
    settings: AnnotationInlayProvider.Settings = AnnotationInlayProvider.Settings(showInferred = true, showExternal = true)
  ) {
    doTestProvider(
      "test.java",
      text,
      AnnotationInlayProvider(),
      settings
    )
  }
}