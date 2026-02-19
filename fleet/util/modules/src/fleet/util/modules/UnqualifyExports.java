package fleet.util.modules;

import java.lang.module.ModuleDescriptor;

final class UnqualifyExports {
  static ModuleDescriptor unqualifyExports(ModuleDescriptor moduleDescriptor) {
    if (moduleDescriptor.isAutomatic()) {
      return moduleDescriptor;
    }
    var builder = moduleDescriptor.isOpen()
                  ? ModuleDescriptor.newOpenModule(moduleDescriptor.name())
                  : ModuleDescriptor.newModule(moduleDescriptor.name());
    moduleDescriptor.version().ifPresent(v -> builder.version(v));
    moduleDescriptor.mainClass().ifPresent(mc -> builder.mainClass(mc));
    for (var use : moduleDescriptor.uses()) {
      builder.uses(use);
    }
    for (var provide : moduleDescriptor.provides()) {
      builder.provides(provide);
    }
    for (var require : moduleDescriptor.requires()) {
      builder.requires(require);
    }
    for (var open : moduleDescriptor.opens()) {
      builder.opens(open);
    }
    for (var export : moduleDescriptor.exports()) {
      // qualified exports between different module layers are ignored for some reason
      // https://github.com/AdoptOpenJDK/openjdk-jdk11/blame/master/src/java.base/share/classes/jdk/internal/loader/Loader.java#L261

      // the important part is to omit [targets]
      // so instead of `exports(export.modifiers(), export.source(), export.targets())` we call
      builder.exports(export.modifiers(), export.source());
    }
    builder.packages(moduleDescriptor.packages());
    return builder.build();
  }
}