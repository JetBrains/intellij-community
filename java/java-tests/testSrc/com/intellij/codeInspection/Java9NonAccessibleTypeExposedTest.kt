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
    addFile("apiPkg/PublicApi.java", "package apiPkg; public class PublicApi {}", MAIN)
    addFile("apiPkg/PackageLocal.java", "package apiPkg; class PackageLocal {}", MAIN)
    addFile("otherPkg/PublicOther.java", "package otherPkg; public class PublicOther {}", MAIN)
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
    addFile("implPkg/NotExported.java", "package implPkg; public class NotExported {}", MAIN)
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
    addFile("apiPkg/PublicOuter.java", """package apiPkg; public class PublicOuter {
  static class PackageLocal {
    public class DoubleNested {}
  }
}""", MAIN)
    highlight("""package apiPkg;
import apiPkg.PublicOuter.PackageLocal;
public class Highlighted {
  private PackageLocal.DoubleNested myVar;
  public <warning descr="The class is not exported from the module">PackageLocal.DoubleNested</warning> getVar() {return myVar;}
  protected void setVar(<warning descr="The class is not exported from the module">PackageLocal.DoubleNested</warning> var) {myVar = var;}
}
""")
  }

  private fun highlight(@Language("JAVA") @NotNull @NonNls text: String) {
    val file = addFile("apiPkg/Highlighted.java", text, MAIN)
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.checkHighlighting()
  }
}
