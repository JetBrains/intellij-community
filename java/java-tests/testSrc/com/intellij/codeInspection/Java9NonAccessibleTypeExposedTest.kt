/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection

import com.intellij.codeInspection.java19modules.Java9NonAccessibleTypeExposedInspection
import com.intellij.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import com.intellij.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull

/**
 * @author Pavel.Dolgov
 */
class Java9NonAccessibleTypeExposedTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(Java9NonAccessibleTypeExposedInspection())
    addFile("module-info.java", "module MAIN { exports apiPkg; exports otherPkg; requires M2; }", MAIN)
    add("apiPkg", "PublicApi", "public class PublicApi {}")
    add("apiPkg", "PackageLocal", "class PackageLocal {}")
    add("otherPkg", "PublicOther", "public class PublicOther {}")
  }

  fun testPrimitives() {
    highlight("""package apiPkg;
public class Highlighted {
  public int i;
  protected int getInt() {return 1;}
  public void run() {}
}""")
  }

  fun testImported() {
    addFile("module-info.java", "module M2 { exports m2Pkg; }", M2)
    addFile("m2Pkg/Exported.java", "package m2Pkg; public class Exported {}", M2)

    highlight("""package apiPkg;
import m2Pkg.Exported;
public class Highlighted {
  public Exported myVar;
  protected Highlighted() {}
  public Highlighted(Exported var) {setVar(var);}
  public Exported getVar() {return myVar;}
  protected void setVar(Exported var) {myVar = var;}
}""")
  }

  fun testPackageLocalExposed() {
    highlight("""package apiPkg;
public class Highlighted {
  public <warning descr="The class is not exported from the module">PackageLocal</warning> myVar;
  protected Highlighted() {}
  public Highlighted(<warning descr="The class is not exported from the module">PackageLocal</warning> var) {setVar(var);}
  public <warning descr="The class is not exported from the module">PackageLocal</warning> getVar() {return myVar;}
  protected void setVar(<warning descr="The class is not exported from the module">PackageLocal</warning> var) {myVar = var;}
}
""")
  }

  fun testPackageLocalEncapsulated() {
    highlight("""package apiPkg;
public class Highlighted {
  private PackageLocal myVar;
  private Highlighted() {}
  Highlighted(PackageLocal var) {setVar(var);}
  PackageLocal getVar() {return myVar;}
  private void setVar(PackageLocal var) {myVar = var;}
}
""")
  }

  fun testPackageLocalUsedLocally() {
    highlight("""package apiPkg;
class Highlighted {
  public PackageLocal myVar;
  protected Highlighted() {}
  public Highlighted(PackageLocal var) {setVar(var);}
  public PackageLocal getVar() {return myVar;}
  protected void setVar(PackageLocal var) {myVar = var;}
}
""")
  }

  fun testPublicApi() {
    highlight("""package apiPkg;
public class Highlighted {
  public PublicApi myVar;
  protected Highlighted() {}
  public Highlighted(PublicApi var) {setVar(var);}
  public PublicApi getVar() {return myVar;}
  protected void setVar(PublicApi var) {myVar = var;}
}
""")
  }

  fun testPublicOther() {
    highlight("""package apiPkg;
import otherPkg.PublicOther;
public class Highlighted {
  public PublicOther myVar;
  protected Highlighted() {}
  public Highlighted(PublicOther var) {setVar(var);}
  public PublicOther getVar() {return myVar;}
  protected void setVar(PublicOther var) {myVar = var;}
}
""")
  }

  fun testPublicNested() {
    highlight("""package apiPkg;
public class Highlighted {
  public class PublicNested {}
  public PublicNested myVar;
  protected Highlighted() {}
  public Highlighted(PublicNested var) {setVar(var);}
  public PublicNested getVar() {return myVar;}
  protected void setVar(PublicNested var) {myVar = var;}
}
""")
  }

  fun testPackageLocalNested() {
    highlight("""package apiPkg;
public class Highlighted {
  class PackageLocalNested {}
  public <warning descr="The class is not exported from the module">PackageLocalNested</warning> myVar;
  protected Highlighted() {}
  public Highlighted(<warning descr="The class is not exported from the module">PackageLocalNested</warning> var) {setVar(var);}
  public <warning descr="The class is not exported from the module">PackageLocalNested</warning> getVar() {return myVar;}
  protected void setVar(<warning descr="The class is not exported from the module">PackageLocalNested</warning> var) {myVar = var;}
}
""")
  }

  fun testPackageLocalInInterface() {
    highlight("""package apiPkg;
public interface Highlighted {
  <warning descr="The class is not exported from the module">PackageLocal</warning> myVar = new PackageLocal();
  <warning descr="The class is not exported from the module">PackageLocal</warning> getVar();
  void setVar(<warning descr="The class is not exported from the module">PackageLocal</warning> var);
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
  public <warning descr="The class is not exported from the module">NotExported</warning> myVar;
  protected Highlighted() {}
  public Highlighted(<warning descr="The class is not exported from the module">NotExported</warning> var) {setVar(var);}
  public <warning descr="The class is not exported from the module">NotExported</warning> getVar() {return myVar;}
  protected void setVar(<warning descr="The class is not exported from the module">NotExported</warning> var) {myVar = var;}
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
  public <warning descr="The class is not exported from the module">PackageLocal.DoubleNested</warning> getVar() {return myVar;}
  protected void setVar(<warning descr="The class is not exported from the module">PackageLocal.DoubleNested</warning> var) {myVar = var;}
}
""")
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
  public void throwsPackageLocal() throws <warning descr="The class is not exported from the module">PackageLocalException</warning> {}
  public void throwsOther() throws OtherException {}
  public void throwsNotExported() throws <warning descr="The class is not exported from the module">NotExportedException</warning> {}
}
""")
  }

  fun testPublicAnnotation() {
    add("apiPkg", "MyAnnotation", "public @interface MyAnnotation {}")
    highlight("""package apiPkg;
@MyAnnotation
public class Highlighted {
  @MyAnnotation public PublicApi field;
  @MyAnnotation public Highlighted() {}
  public Highlighted(@MyAnnotation PublicApi s) {field=s;}
  @MyAnnotation protected void init() {}
  protected @MyAnnotation PublicApi peek() {return field;}
  public void set(@MyAnnotation PublicApi s) {field=s;}
}
""")
  }

  fun testPackageLocalAnnotation() {
    add("apiPkg", "MyAnnotation", "@interface MyAnnotation {}")
    highlight("""package apiPkg;
@<warning descr="The class is not exported from the module">MyAnnotation</warning>
public class Highlighted {
  @<warning descr="The class is not exported from the module">MyAnnotation</warning> public PublicApi field;
  @<warning descr="The class is not exported from the module">MyAnnotation</warning> public Highlighted() {}
  public Highlighted(@<warning descr="The class is not exported from the module">MyAnnotation</warning> PublicApi s) {field=s;}
  @<warning descr="The class is not exported from the module">MyAnnotation</warning> protected void init() {}
  protected @<warning descr="The class is not exported from the module">MyAnnotation</warning> PublicApi peek() {return field;}
  public void set(@<warning descr="The class is not exported from the module">MyAnnotation</warning> PublicApi s) {field=s;}
}
""")
  }

  fun testNotExportedAnnotation() {
    add("implPkg", "MyAnnotation", "public @interface MyAnnotation {}")
    highlight("""package apiPkg;
import implPkg.MyAnnotation;
@<warning descr="The class is not exported from the module">MyAnnotation</warning>
public class Highlighted {
  @<warning descr="The class is not exported from the module">MyAnnotation</warning> public PublicApi field;
  @<warning descr="The class is not exported from the module">MyAnnotation</warning> public Highlighted() {}
  public Highlighted(@<warning descr="The class is not exported from the module">MyAnnotation</warning> PublicApi s) {field=s;}
  @<warning descr="The class is not exported from the module">MyAnnotation</warning> protected void init() {}
  protected @<warning descr="The class is not exported from the module">MyAnnotation</warning> PublicApi peek() {return field;}
  public void set(@<warning descr="The class is not exported from the module">MyAnnotation</warning> PublicApi s) {field=s;}
}
""")
  }

  fun testTypeParameterAndUseAnnotation() {
    highlight("""package apiPkg;
import java.lang.annotation.*;
import java.util.*;
@Highlighted.PublicAnnotation
@<warning descr="The class is not exported from the module">Highlighted.PackageLocalAnnotation</warning>
public class Highlighted {
  @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE}) public @interface PublicAnnotation {}
  @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE}) @interface PackageLocalAnnotation {}

  public class C1<@PublicAnnotation T> {
    public void foo(Set<@PublicAnnotation String> s) {}
    protected <@PublicAnnotation X> void bar(X x) {}
    protected List<@PublicAnnotation T> baz() { return new ArrayList<@PublicAnnotation T>();}
  }
  public class C2<@<warning descr="The class is not exported from the module">PackageLocalAnnotation</warning> T> {
    public void foo(Set<@<warning descr="The class is not exported from the module">PackageLocalAnnotation</warning> String> s) {}
    protected <@<warning descr="The class is not exported from the module">PackageLocalAnnotation</warning> X> void bar(X x) {}
    protected List<@<warning descr="The class is not exported from the module">PackageLocalAnnotation</warning> T> baz() {
      return new ArrayList<@<warning descr="The class is not exported from the module">PackageLocalAnnotation</warning> T>();
    }
  }
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
