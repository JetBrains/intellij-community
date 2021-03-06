/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInspection

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil
import com.intellij.codeInspection.dataFlow.MutationSignature
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic
/**
 * @author peter
 */
@CompileStatic
class PurityInferenceFromSourceTest extends LightJavaCodeInsightFixtureTestCase {

  void "test getter"() {
    assertPure """
Object getField() {
  return field;
}
"""
  }

  void "test setter"() {
    assertImpure """
void setField(String s) {
  field = s;
}
"""
  }

  void "test unknown"() {
    assertImpure """
int random() {
  launchMissiles();
  return 2;
}
"""
  }

  void "test print"() {
    assertImpure """
int random() {
  System.out.println("hello");
  return 2;
}
"""
  }

  void "test local var assignment"() {
    assertPure """
int random(boolean b) {
  int i = 4;
  if (b) {
    i = 2;
  } else {
    i++;
  }
  return i;
}
"""
  }

  void "test local array var assignment"() {
    assertPure """
int[] randomArray() {
  int[] i = new int[0];
  i[0] = random();
  return i;
}
int random() { return 2; }
"""
  }

  void "test field array assignment"() {
    assertImpure """
int[] randomArray() {
  i[0] = random();
  return i;
}
int random() { return 2; }
  int[] i = new int[0];
"""
  }

  void "test field array assignment as local var"() {
    assertImpure """
int[] randomArray() {
  int[] local = i;
  local[0] = random();
  return local;
}
int random() { return 2; }
  int[] i = new int[0];
"""
  }

  void "test use explicit pure contract"() {
    assertPure """
int method() {
  return smthPure();
}
@org.jetbrains.annotations.Contract(pure=true) native int smthPure();
"""
  }

  void "test don't analyze more than one call"() {
    assertImpure """
int method() {
  return smthPure(smthPure2());
}
int smthPure(int i) { return i; }
int smthPure2() { return 42; }
"""
  }

  void "test empty constructor"() {
    assertPure """
public Foo() {
}
"""
  }

  void "test field writes"() {
    assertPure """
int x;
int y;

public Foo() {
  x = 5;
  this.y = 10;
}
"""
  }
  
  void "test constructor calling"() {
    // IDEA-192251
    assertPure """
    private final int i;
    private final int j;
    private final Foo a;

    Foo(int i, Foo a) {
        this.i = i;
        this.j = getConstant();
        this.a = a;
    }
    
    private int getConstant() {return 42;}
"""
  }

  void "test delegating field writes"() {
    assertPure """
int x;
int y;

public Foo() {
  this(5, 10);
}

Foo(int x, int y) {
  this.x = x;
  this.y = y;
}
"""
  }

  void "test delegating unknown writes"() {
    assertImpure """
int x;
int y;

public Foo() {
  this(5, 10);
}

Foo(int x, int y) {
  this.x = x;
  this.z = y;
}
"""
  }

  void "test static field writes"() {
    assertImpure """
int x;
static int y;

public Foo() {
  x = 5;
  this.y = 10;
}
"""
  }

  void "test calling constructor with side effects"() {
    assertImpure """
    Object newExample() {
        return new Example1();
    }

    private static int created = 0;

    Example1() {
        created++;
    }
    """
  }

  void "test anonymous class initializer"() {
    assertImpure """
    Object smth() {
        return new I(){{ created++; }};
    }

    private static int created = 0;
    
    interface I {}
    """
  }

  void "test simple anonymous class creation"() {
    assertPure """
    Object smth() {
        return new I(){};
    }
    
    interface I {}
    """
  }

  void "test anonymous class with constructor side effect"() {
    assertImpure """
    Object smth() {
        return new I(){};
    }
    
    class I {
      I() {
        unknown();
      }
    }
    """
  }

  void "test anonymous class with arguments"() {
    assertImpure """
    Object smth() {
        return new I(unknown()){};
    }
    
    class I {
      I(int a) {}
    }
    """
  }

  void "test class with impure initializer creation"() {
    assertImpure """
    Object smth() {
        return new I(42);
    }
    
    class I {
      I(int answer) {}
      {
        launchMissiles();
      }
    }
    """
  }

  void "test class with impure static initializer creation"() {
    assertPure """
    Object smth() {
        return new I(42);
    }
    
    class I {
      I(int answer) {}
      static {
        launchMissiles();
      }
    }
    """
  }

  void "test class with pure field initializers"() {
    assertPure """
    Object smth() {
        return new I(42);
    }
    
    class I {
      int x = 5;
      I(int answer) {x+=answer;}
    }
    """
  }

  void "test class with impure field initializers"() {
    assertImpure """
    Object smth() {
        return new I(42);
    }
    
    class I {
      int x = launchMissiles();
      I(int answer) {x+=answer;}
    }
    """
  }

  void "test class with superclass"() {
    assertImpure """
    Object smth() {
        return new I(42);
    }
    
    class I extends Foo {
      // cannot determine purity yet as should delegate to super ctor
      I(int answer) {}
    }
    """
  }

  void "test delegate to a method calling local class constructor"() {
    myFixture.addClass("""
class Another { 
  static Object method() {
    class LocalClass {
      LocalClass() { launchMissiles(); }
    }
    return new LocalClass();
  } 
}
  """)
    assertImpure """
    Object smth() {
        return Another.method();
    }
    """
  }
  
  void "test increment field"() {
    assertMutatesThis """
    int x = 0;

    private void increment() {
        x++;
    }
"""
  }
  
  void "test delegate to setter"() {
    assertMutatesThis """
    int x = 0;

    private void foo() {
        setX(2);
    }
    
    private void setX(int x) {
        this.x = x;
    }
"""
  }
  
  void "test setter in ctor"() {
    assertPure """
    int x = 0;

    public Foo() {
        setX(2);
    }
    
    private void setX(int x) {
        this.x = x;
    }
"""
  }

  void "test plain field read"() {
    assertPure """
int x;

int get() {
  return x;
}
"""
  }

  void "test volatile field read"() {
    assertImpure """
volatile int x;

int get() {
  return x;
}
"""
  }

  void "test assertNotNull is pure"() {
    assertPure """
static void assertNotNull(Object val) {
  if(val == null) throw new AssertionError();
}"""
  }

  void "test recursive factorial"() {
    assertPure """int factorial(int n) { return n == 1 ? 1 : factorial(n - 1) * n;}"""
  }

  void "test calling static method with the same signature in the subclass"() {
    RecursionManager.assertOnMissedCache(testRootDisposable)
    def clazz = myFixture.addClass """
class Super {
  static void foo() { Sub.foo(); }
}

class Sub extends Super {
  static void foo() {
    unknown();
    unknown2();
  }
}

"""
    assert !JavaMethodContractUtil.isPure(clazz.methods[0])
  }

  void "test super static method does not affect purity"() {
    RecursionManager.assertOnMissedCache(testRootDisposable)
    def clazz = myFixture.addClass """
class Sub extends Super {
  static void foo() {
    unknown();
    unknown2();
  }
}

class Super {
  static void foo() { }
}
"""
    assert !JavaMethodContractUtil.isPure(clazz.methods[0])
    assert JavaMethodContractUtil.isPure(clazz.superClass.methods[0])
  }
  
  void "test enum method"() {
    def clazz = myFixture.addClass """
enum X {
  A;
  
  Iterable<?> onXyz() {
    return java.util.Collections.emptyList();
  }
}
"""
    assert JavaMethodContractUtil.isPure(clazz.methods[0])
  }
  
  void "test enum method with subclass"() {
    def clazz = myFixture.addClass """
enum X {
  A, B {};
  
  Iterable<?> onXyz() {
    return java.util.Collections.emptyList();
  }
}
"""
    assert !JavaMethodContractUtil.isPure(clazz.methods[0])
  }

  private void assertPure(String classBody) {
    assertMutationSignature(classBody, MutationSignature.pure())
  }

  private void assertImpure(String classBody) {
    assertMutationSignature(classBody, MutationSignature.unknown())
  }

  private void assertMutatesThis(String classBody) {
    assertMutationSignature(classBody, MutationSignature.pure().alsoMutatesThis())
  }

  private void assertMutationSignature(String classBody, MutationSignature expected) {
    def clazz = myFixture.addClass("final class Foo { $classBody }")
    assert !((PsiFileImpl)clazz.containingFile).contentsLoaded
    def signature = JavaSourceInference.inferMutationSignature((PsiMethodImpl)clazz.methods[0])
    assert !((PsiFileImpl)clazz.containingFile).contentsLoaded
    assert expected == signature
  }
}
