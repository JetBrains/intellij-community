package fleet.util.modules;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public final class JavaModules {

  private static Method addOpensMethod;
  private static Method addOpensToAllUnnamedMethod;
  private static Method addExportsMethod;
  private static Method addExportsToAllUnnamedMethod;
  private static Method addReadsMethod;
  private static Method addReadsAllUnnamedMethod;

  public static void openAndExportModules(ModuleLayer moduleLayer, List<String> commands) {
    commands.forEach((line) -> {
      if (!line.isBlank()) {
        var values = line.split("=", 3);
        var s = values[0];
        if (s.equals("--add-exports")) {
          var targetModule = values[2];
          var moduleAndPackage = values[1].split("/", 2);
          moduleLayer.findModule(moduleAndPackage[0]).ifPresent((module) -> {
            var packageName = moduleAndPackage[1];
            if (targetModule.equals("ALL-UNNAMED")) {
              try {
                addExportsToAllUnnamedMethod().invoke(module, packageName);
              }
              catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
              }
            }
            else {
              moduleLayer.findModule(targetModule).ifPresent((tm) -> {
                try {
                  addExportsMethod().invoke(module, packageName, tm);
                }
                catch (IllegalAccessException | InvocationTargetException e) {
                  throw new RuntimeException(e);
                }
              });
            }
          });
        }
        else if (s.equals("--add-opens")) {
          var targetModuleName = values[2];
          var moduleAndPackage = values[1].split("/", 2);
          var moduleName = moduleAndPackage[0];
          var packageName = moduleAndPackage[1];
          moduleLayer.findModule(moduleName).ifPresent((module) -> {
            if (targetModuleName.equals("ALL-UNNAMED")) {
              try {
                addOpensToAllUnnamedMethod().invoke(module, packageName);
              }
              catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
              }
            }
            else {
              moduleLayer.findModule(targetModuleName).ifPresent((targetModule) -> {
                try {
                  addOpensMethod().invoke(module, packageName, targetModule);
                }
                catch (IllegalAccessException | InvocationTargetException e) {
                  throw new RuntimeException(e);
                }
              });
            }
          });
        }
        else if (s.equals("--add-reads")) {
          moduleLayer.findModule(values[1]).ifPresent((module) -> {
            var targets = values[2].split(",");
            for (var targetModuleName : targets) {
              if (targetModuleName.equals("ALL-UNNAMED")) {
                try {
                  addReadsAllUnnamedMethod().invoke(module);
                }
                catch (IllegalAccessException | InvocationTargetException e) {
                  throw new RuntimeException(e);
                }
              }
              else {
                moduleLayer.findModule(targetModuleName).ifPresent((targetModule) -> {
                  try {
                    addReadsMethod().invoke(module, targetModule);
                  }
                  catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                  }
                });
              }
            }
          });
        }
        else {
          throw new RuntimeException("Unknown option in line " + line);
        }
      }
    });
  }

  private static synchronized Method addOpensMethod() {
    if (addOpensMethod == null) {
      try {
        var method = Module.class.getDeclaredMethod("implAddOpens", String.class, Module.class);
        method.trySetAccessible();
        addOpensMethod = method;
      }
      catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
    return addOpensMethod;
  }

  private static synchronized Method addOpensToAllUnnamedMethod() {
    if (addOpensToAllUnnamedMethod == null) {
      try {
        var method = Module.class.getDeclaredMethod("implAddOpensToAllUnnamed", String.class);
        method.trySetAccessible();
        addOpensToAllUnnamedMethod = method;
      }
      catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
    return addOpensToAllUnnamedMethod;
  }

  private static synchronized Method addExportsMethod() {
    if (addExportsMethod == null) {
      try {
        var method = Module.class.getDeclaredMethod("implAddExports", String.class, Module.class);
        method.trySetAccessible();
        addExportsMethod = method;
      }
      catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
    return addExportsMethod;
  }

  private static synchronized Method addExportsToAllUnnamedMethod() {
    if (addExportsToAllUnnamedMethod == null) {
      try {
        var method = Module.class.getDeclaredMethod("implAddExportsToAllUnnamed", String.class);
        method.trySetAccessible();
        addExportsToAllUnnamedMethod = method;
      }
      catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
    return addExportsToAllUnnamedMethod;
  }

  private static synchronized Method addReadsMethod() {
    if (addReadsMethod == null) {
      try {
        var method = Module.class.getDeclaredMethod("implAddReads", Module.class);
        method.trySetAccessible();
        addReadsMethod = method;
      }
      catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
    return addReadsMethod;
  }

  private static synchronized Method addReadsAllUnnamedMethod() {
    if (addReadsAllUnnamedMethod == null) {
      try {
        var method = Module.class.getDeclaredMethod("implAddReadsAllUnnamed");
        method.trySetAccessible();
        addReadsAllUnnamedMethod = method;
      }
      catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
    return addReadsAllUnnamedMethod;
  }
}