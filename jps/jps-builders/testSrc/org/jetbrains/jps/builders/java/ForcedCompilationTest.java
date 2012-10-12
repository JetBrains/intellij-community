package org.jetbrains.jps.builders.java;

import com.intellij.util.PathUtil;
import org.jetbrains.jps.builders.CompileScopeTestBuilder;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class ForcedCompilationTest extends JpsBuildTestCase {
  public void testRecompileDependentAfterForcedCompilation() {
    String srcRoot = PathUtil.getParentPath(createFile("src/A.java", "class A{ { new B(); } }"));
    String b = createFile("depSrc/B.java", "public class B{}");
    JpsModule main = addModule("main", srcRoot);
    JpsModule dep = addModule("dep", PathUtil.getParentPath(b));
    main.getDependenciesList().addModuleDependency(dep);
    rebuildAll();

    change(b, "public class B{ public B(int i){} }");
    doBuild(CompileScopeTestBuilder.recompile().module(dep)).assertSuccessful();
    doBuild(CompileScopeTestBuilder.make().module(main)).assertFailed();
  }
}
