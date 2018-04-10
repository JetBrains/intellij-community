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

import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN
import com.siyeh.ig.visibility.ClassEscapesItsScopeInspection
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull

/**
 * @author Pavel.Dolgov
 */
class Java9NonAccessibleTypeExposedTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ClassEscapesItsScopeInspection())

    addFile("module-info.java", "module MAIN { exports apiPkg; exports otherPkg; requires M2; }", MAIN)
    addFile("module-info.java", "module M2 { exports m2Pkg; }", M2)
    add("apiPkg", "PublicApi", "public class PublicApi {}")
    add("apiPkg", "PackageLocal", "class PackageLocal {}")
    add("otherPkg", "PublicOther", "public class PublicOther {}")
  }

  fun testPrimitives() {
    highlight("""package apiPkg;
public class Highlighted {
  public int i;
  public int getInt() {return i;}
  public void setInt(int n) {i=n;}

  protected Long l;
  protected Long getLong() {return l;}
  protected void setLong(Long n) {l=n;}

  public void run() {}
}""")
  }

  fun testExported() {
    addFile("m2Pkg/Exported.java", "package m2Pkg; public class Exported {}", M2)

    highlight("""package apiPkg;
import m2Pkg.Exported;
public class Highlighted {
  static { Exported tmp = new Exported(); System.out.println(tmp);}
  public Exported myVar;
  protected Highlighted() {}
  public Highlighted(Exported var) {
    Exported tmp = new Exported(); myVar = var!= null ? var : tmp;
  }
  public Exported getVar() {return myVar;}
  protected void setVar(Exported var) {myVar = var;}
}""")
  }

  fun testPackageLocalExposed() {
    highlight("""package apiPkg;
public class Highlighted {
  static { PackageLocal tmp = new PackageLocal(); System.out.println(tmp);}
  public <warning descr="Class 'PackageLocal' is not exported from module 'MAIN'">PackageLocal</warning> myVar;
  protected Highlighted() {}
  public Highlighted(<warning descr="Class 'PackageLocal' is not exported from module 'MAIN'">PackageLocal</warning> var) {
    PackageLocal tmp = new PackageLocal(); myVar = var!= null ? var : tmp;
  }
  public <warning descr="Class 'PackageLocal' is not exported from module 'MAIN'">PackageLocal</warning> getVar() {return myVar;}
  protected void setVar(<warning descr="Class 'PackageLocal' is not exported from module 'MAIN'">PackageLocal</warning> var) {myVar = var;}
}
""")
  }

  fun testPackageLocalEncapsulated() {
    highlight("""package apiPkg;
public class Highlighted {
  static { PackageLocal tmp = new PackageLocal(); System.out.println(tmp);}
  private PackageLocal myVar;
  private Highlighted() {}
  Highlighted(PackageLocal var) {
    PackageLocal tmp = new PackageLocal(); myVar = var!= null ? var : tmp;
  }
  PackageLocal getVar() {return myVar;}
  private void setVar(PackageLocal var) {myVar = var;}
}
""")
  }
  fun testPackageLocalEncapsulated2() {
    highlight("""package apiPkg;
public class Highlighted {
  Highlighted(PackageLocal var) {
  }
}
""")
  }

  fun testPackageLocalUsedLocally() {
    highlight("""package apiPkg;
class Highlighted {
  static { PackageLocal tmp = new PackageLocal(); System.out.println(tmp);}
  public PackageLocal myVar;
  protected Highlighted() {}
  public Highlighted(PackageLocal var) {
    PackageLocal tmp = new PackageLocal(); myVar = var!= null ? var : tmp;
  }
  public PackageLocal getVar() {return myVar;}
  protected void setVar(PackageLocal var) {myVar = var;}
}
""")
  }

  fun testPublicApi() {
    highlight("""package apiPkg;
public class Highlighted {
  static { PublicApi tmp = new PublicApi(); System.out.println(tmp);}
  public PublicApi myVar;
  protected Highlighted() {}
  public Highlighted(PublicApi var) {
    PublicApi tmp = new PublicApi(); myVar = var!= null ? var : tmp;
  }
  public PublicApi getVar() {return myVar;}
  protected void setVar(PublicApi var) {myVar = var;}
}
""")
  }

  fun testPublicOther() {
    highlight("""package apiPkg;
import otherPkg.PublicOther;
public class Highlighted {
  static { PublicOther tmp = new PublicOther(); System.out.println(tmp);}
  public PublicOther myVar;
  protected Highlighted() {}
  public Highlighted(PublicOther var) {
    PublicOther tmp = new PublicOther(); myVar = var!= null ? var : tmp;
  }
  public PublicOther getVar() {return myVar;}
  protected void setVar(PublicOther var) {myVar = var;}
}
""")
  }

  fun testPublicNested() {
    highlight("""package apiPkg;
public class Highlighted {
  public class PublicNested {}
  { PublicNested tmp = new PublicNested(); System.out.println(tmp);}
  public PublicNested myVar;
  protected Highlighted() {}
  public Highlighted(PublicNested var) {
    PublicNested tmp = new PublicNested(); myVar = var!= null ? var : tmp;
  }
  public PublicNested getVar() {return myVar;}
  protected void setVar(PublicNested var) {myVar = var;}
}
""")
  }

  fun testPackageLocalNested() {
    highlight("""package apiPkg;
public class Highlighted {
  class PackageLocalNested {}
  { PackageLocalNested tmp = new PackageLocalNested(); System.out.println(tmp);}
  public <warning descr="Class 'PackageLocalNested' is not exported from module 'MAIN'">PackageLocalNested</warning> myVar;
  protected Highlighted() {}
  public Highlighted(<warning descr="Class 'PackageLocalNested' is not exported from module 'MAIN'">PackageLocalNested</warning> var) {
    PackageLocalNested tmp = new PackageLocalNested(); myVar = var!= null ? var : tmp;
  }
  public <warning descr="Class 'PackageLocalNested' is not exported from module 'MAIN'">PackageLocalNested</warning> getVar() {return myVar;}
  protected void setVar(<warning descr="Class 'PackageLocalNested' is not exported from module 'MAIN'">PackageLocalNested</warning> var) {myVar = var;}
}
""")
  }

  fun testPackageLocalInInterface() {
    highlight("""package apiPkg;
public interface Highlighted {
  <warning descr="Class 'PackageLocal' is not exported from module 'MAIN'">PackageLocal</warning> myVar = new PackageLocal();
  <warning descr="Class 'PackageLocal' is not exported from module 'MAIN'">PackageLocal</warning> getVar();
  void setVar(<warning descr="Class 'PackageLocal' is not exported from module 'MAIN'">PackageLocal</warning> var);
}
""")
  }

  fun testPublicInInterface() {
    highlight("""package apiPkg;
public interface Highlighted {
  PublicApi myVar = new PublicApi();
  PublicApi getVar();
  void setVar(PublicApi var);
}
""")
  }

  fun testNotExportedPackage() {
    add("implPkg", "NotExported", "public class NotExported {}")
    highlight("""package apiPkg;
import implPkg.NotExported;
public class Highlighted {
  public <warning descr="Class 'NotExported' is not exported from module 'MAIN'">NotExported</warning> myVar;
  protected Highlighted() {}
  public Highlighted(<warning descr="Class 'NotExported' is not exported from module 'MAIN'">NotExported</warning> var) {setVar(var);}
  public <warning descr="Class 'NotExported' is not exported from module 'MAIN'">NotExported</warning> getVar() {return myVar;}
  protected void setVar(<warning descr="Class 'NotExported' is not exported from module 'MAIN'">NotExported</warning> var) {myVar = var;}
}
""")
  }

  fun testDoubleNested() {
    add("apiPkg", "PublicOuter", """public class PublicOuter {
  static class PackageLocal {
    public class DoubleNested {}
  }
}""")
    highlight("""package apiPkg;
import apiPkg.PublicOuter.PackageLocal;
public class Highlighted {
  private PackageLocal.DoubleNested myVar;
  public <warning descr="Class 'PackageLocal.DoubleNested' is not exported from module 'MAIN'">PackageLocal.DoubleNested</warning> getVar() {return myVar;}
  protected void setVar(<warning descr="Class 'PackageLocal.DoubleNested' is not exported from module 'MAIN'">PackageLocal.DoubleNested</warning> var) {myVar = var;}
}
""")
  }

  fun testExportedArray() {
    addFile("m2Pkg/Exported.java", "package m2Pkg; public class Exported {}", M2)
    highlight("""package apiPkg;
import m2Pkg.Exported;
import java.util.*;
public class Highlighted {
  public Exported[] myVar;
  protected Highlighted(List<Exported[]> list) {Iterator<Exported[]> it = list.iterator(); myVar = it.next();}
  public Highlighted(Exported[] var) {myVar = var;}
  public Exported[] getVar() {return myVar;}
  protected void setVar(Exported[][] var) {myVar = var[0];}
}""")
  }

  fun testNotExportedArray() {
    add("implPkg", "NotExported", "public class NotExported {}")
    highlight("""package apiPkg;
import implPkg.NotExported;
import java.util.*;
public class Highlighted {
  public <warning descr="Class 'NotExported' is not exported from module 'MAIN'">NotExported</warning>[] myVar;
  protected Highlighted(List<<warning descr="Class 'NotExported' is not exported from module 'MAIN'">NotExported</warning>[]> list) {Iterator<NotExported[]> it = list.iterator(); myVar = it.next();}
  public Highlighted(<warning descr="Class 'NotExported' is not exported from module 'MAIN'">NotExported</warning>[] var) {myVar = var;}
  public <warning descr="Class 'NotExported' is not exported from module 'MAIN'">NotExported</warning>[] getVar() {return myVar;}
  protected void setVar(<warning descr="Class 'NotExported' is not exported from module 'MAIN'">NotExported</warning>[][] var) {myVar = var[0];}
}""")
  }

  fun testThrows() {
    add("apiPkg", "PublicException", "public class PublicException extends Exception {}")
    add("apiPkg", "PackageLocalException", "class PackageLocalException extends Exception {}")
    add("otherPkg", "OtherException", "public class OtherException extends Exception {}")
    add("implPkg", "NotExportedException", "public class NotExportedException extends Exception {}")
    highlight("""package apiPkg;
import otherPkg.*;
import implPkg.*;
public class Highlighted {
  public void throwsPublic() throws PublicException {}
  public void throwsPackageLocal() throws <warning descr="Class 'PackageLocalException' is not exported from module 'MAIN'">PackageLocalException</warning> {}
  public void throwsOther() throws OtherException {}
  public void throwsNotExported() throws <warning descr="Class 'NotExportedException' is not exported from module 'MAIN'">NotExportedException</warning> {}
}
""")
  }

  fun testGenericPublic() {
    add("apiPkg", "MyInterface", "public interface MyInterface {}")
    add("apiPkg", "MyClass", "public class MyClass implements MyInterface {}")
    highlight("""package apiPkg;
import java.util.*;
public class Highlighted<T extends MyInterface> {
  protected Set<T> get1() { return new HashSet<>();}
  public Set<MyClass> get2() { return new HashSet<MyClass>();}
  protected Set<? extends MyClass> get3() { return new HashSet<>();}
  public <X extends Object&MyInterface> Set<X> get4() { return new HashSet<>();}
  public Map<String, Set<MyInterface>> get5() {return new HashMap<>();}
  public void copy1(Set<MyInterface> s) {}
  public void copy2(Set<? super MyClass> s) {}

  public static class Nested1<T extends MyClass&Iterable<MyInterface>> {
    public Iterator<MyInterface> iterator() {return null;}
  }
  public static class Nested2<T extends MyInterface&AutoCloseable> {
    public void close(){}
  }
  public interface Nested3<X extends Iterable<MyInterface>> {}
  public interface Nested4<X extends Iterable<? extends MyInterface>> {}
}
""")
  }

  fun testGenericNotExported() {
    add("implPkg", "MyInterface", "public interface MyInterface {}")
    add("implPkg", "MyClass", "public class MyClass implements MyInterface {}")
    highlight("""package apiPkg;
import java.util.*;
import implPkg.*;
public class Highlighted<T extends MyInterface> {
  protected Set<T> get1() { return new HashSet<>();}
  public Set<<warning descr="Class 'MyClass' is not exported from module 'MAIN'">MyClass</warning>> get2() { return new HashSet<MyClass>();}
  protected Set<? extends <warning descr="Class 'MyClass' is not exported from module 'MAIN'">MyClass</warning>> get3() { return new HashSet<>();}
  public <X extends Object&<warning descr="Class 'MyInterface' is not exported from module 'MAIN'">MyInterface</warning>> Set<X> get4() { return new HashSet<>();}
  public Map<String, Set<<warning descr="Class 'MyInterface' is not exported from module 'MAIN'">MyInterface</warning>>> get5() {return new HashMap<>();}
  public void copy1(Set<<warning descr="Class 'MyInterface' is not exported from module 'MAIN'">MyInterface</warning>> s) {}
  public void copy2(Set<? super <warning descr="Class 'MyClass' is not exported from module 'MAIN'">MyClass</warning>> s) {}

  public static class Nested1<T extends MyClass&
      Iterable<MyInterface>> {
    public Iterator<<warning descr="Class 'MyInterface' is not exported from module 'MAIN'">MyInterface</warning>> iterator() {return null;}
  }
  public static class Nested2<T extends MyInterface&AutoCloseable> {
    public void close() {}
  }
  public interface Nested3<X extends Iterable<MyInterface>> {}
  public interface Nested4<X extends Iterable<? extends MyInterface>> {}
}
""")
  }

  private fun highlight(@Language("JAVA") @NotNull @NonNls text: String) {
    val file = addFile("apiPkg/Highlighted.java", text, MAIN)
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.checkHighlighting()
  }

  private fun add(packageName: String, className: String, @Language("JAVA") @NotNull @NonNls text: String) {
    addFile("$packageName/$className.java", "package $packageName; $text", MAIN)
  }
}
