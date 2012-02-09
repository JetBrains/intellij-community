package org.jetbrains.ether.dependencyView;

import org.objectweb.asm.ClassReader;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 28.01.11
 * Time: 15:55
 * To change this template use File | Settings | File Templates.
 */
public class Callbacks {
  public interface SourceFileNameLookup {
    String get(String sourceAttribute);
  }

  public static SourceFileNameLookup getDefaultLookup(final String name) {
    return new SourceFileNameLookup() {
      public String get(final String sourceAttribute) {
        return name;
      }
    };
  }

  public interface Backend {
    Collection<String> getClassFiles();
    void associate(String classFileName, SourceFileNameLookup sourceLookup, ClassReader cr);
    void markOverride(String className, String methodName, String methodSignature);
    void registerConstantUsage(String className, String fieldName, String fieldOwner);
    void registerImports(Collection<String> imports, String rootClass);
  }
}
