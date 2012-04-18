package org.jetbrains.ether.dependencyView;

import org.jetbrains.asm4.ClassReader;

import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 28.01.11
 * Time: 15:55
 * To change this template use File | Settings | File Templates.
 */
public class Callbacks {

  public interface Backend {
    Collection<String> getClassFiles();
    void associate(String classFileName, String sourceFileName, ClassReader cr);
    void registerConstantUsage(String className, String fieldName, String fieldOwner);
    void registerImports(String className, Collection<String> imports, Collection<String> staticImports);
  }
}
