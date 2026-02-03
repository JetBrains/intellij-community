package fleet.util.modules;

import org.jetbrains.annotations.NotNull;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static fleet.util.modules.WithDescriptorModuleFinder.compositeJarModuleReference;

/**
 * Collection of hacks to make debug probes work in Fleet production runtime.
 * <p>
 * Wondering what is happening here?
 * Take a look <a href="https://github.com/Kotlin/kotlinx.coroutines/issues/4124">there</a>
 */
public final class DebugProbesHack {
  public static final String fleetRuntimeModuleName = "fleet.kotlin.runtime";

  static final Set<String> kotlinRuntimeModuleNames = Set.of("kotlin.stdlib", "kotlinx.coroutines.core", "kotlinx.coroutines.debug");

  /**
   * Modules declared in `kotlinx.coroutines.debug` module incorrectly (redundant as bytebuddy is shadowed in `kotlinx.coroutines.debug`).
   * <p>
   * See <a href="https://github.com/Kotlin/kotlinx.coroutines/issues/4124">Kotlin/kotlinx.coroutines issue 4124</a>
   */
  static final Set<String> misdeclaredKotlinxCoroutinesDebugModules = Set.of("net.bytebuddy.agent", "net.bytebuddy");

  /**
   * Modules declared in `kotlinx.coroutines.debug` module that we never use and do not want to fulfill requirement.
   * These modules are used only in test-specific helpers for JUnit and we do not want to bring JUnit to our production runtime classpath!
   * <p>
   * See <a href="https://github.com/Kotlin/kotlinx.coroutines/issues/4124">Kotlin/kotlinx.coroutines issue 4124</a>
   */
  static final Set<String> unwantedKotlinxCoroutinesDebugModules = Set.of("org.junit.jupiter.api", "org.junit.platform.commons");

  /**
   * Builds the composite jar module reference of the {@link fleetRuntimeModuleName}
   */
  static ModuleReference buildFleetKotlinRuntimeModuleReference(Map<String, ModuleInfo.WithDescriptor> moduleMap,
                                                                FleetModuleFinderLogger logger) {
    var modules = kotlinRuntimeModuleNames.stream().map((name) -> moduleMap.get(name)).filter((module) -> module != null)
      .toArray(ModuleInfo.WithDescriptor[]::new);
    if (modules.length != kotlinRuntimeModuleNames.size()) {
      var names = Arrays.stream(modules).map((m) -> m.descriptor().name()).collect(Collectors.toSet());
      throw new IllegalStateException(fleetRuntimeModuleName +
                                      " module can only be created when the module layer has all three modules: " +
                                      kotlinRuntimeModuleNames +
                                      ", got " +
                                      names +
                                      ".\n" +
                                      "Probably a bug in the build script put the missing module(s) in a parent module layer.");
    }
    var descriptorOfMergedModule = bundleIntoAKotlinRuntimeModule(modules);
    return compositeJarModuleReference(descriptorOfMergedModule, logger, modules);
  }

  static ModuleDescriptor bundleIntoAKotlinRuntimeModule(ModuleInfo.WithDescriptor... modulesToMerge) {
    var moduleNames =
      Arrays.stream(modulesToMerge).map(ModuleInfo.WithDescriptor::descriptor).map(ModuleDescriptor::name).collect(Collectors.toSet());
    var toIgnore = new HashSet<String>();
    toIgnore.addAll(misdeclaredKotlinxCoroutinesDebugModules);
    toIgnore.addAll(unwantedKotlinxCoroutinesDebugModules);
    toIgnore.addAll(moduleNames);

    var builder = ModuleDescriptor.newModule(fleetRuntimeModuleName);
    var alreadyAddedRequires = new HashSet<String>();
    for (ModuleInfo.WithDescriptor module : modulesToMerge) {
      fillBuilderFromModule(builder, toIgnore, module.descriptor(), alreadyAddedRequires);
    }
    return builder
      // The following two lines are about misconfigured (missing) in `kotlinx.coroutines.debug` module-info.java, see https://github.com/Kotlin/kotlinx.coroutines/issues/4124
      // These would be required to make `kotlinx.coroutines.debug` module (`fleet.kotlin.runtime` in our case) working under normal circumstance.
      // However, because we are not using bytebuddy injection in our runtime (we manually inject debug probe in `kotlin.stdlib` instead at compile time)
      // it is fine to leave them not specified, that's why the next two lines are commented out.
      // .requires("com.sun.jna")
      // .exports("kotlinx.coroutines.repackaged.net.bytebuddy.agent", Collections.singleton("com.sun.jna"))
    .build();
  }

  // TODO: this is very similar to [fillBuilderFromModule] and to [UnqualifyExports#unqualifyExports], could we merge them?
  static ModuleDescriptor substituteModules(ModuleDescriptor moduleDescriptor) {
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

    var toSubstitute = new HashSet<ModuleDescriptor.Requires>();
    for (var req : moduleDescriptor.requires()) {
      if (kotlinRuntimeModuleNames.contains(req.name())) {
        if (req.compiledVersion().isPresent() || req.rawCompiledVersion().isPresent()) {
          throw new IllegalStateException(
            req.name() + " cannot be declared with compiledVersion, found such declaration in module " + moduleDescriptor.name());
        }
        if (req.modifiers().contains(ModuleDescriptor.Requires.Modifier.MANDATED)) {
          throw new IllegalStateException(req.name() +
                                          " cannot be declared with " +
                                          ModuleDescriptor.Requires.Modifier.MANDATED +
                                          " modifier in module " +
                                          moduleDescriptor.name());
        }
        if (req.modifiers().contains(ModuleDescriptor.Requires.Modifier.SYNTHETIC)) {
          throw new IllegalStateException(req.name() +
                                          " cannot be declared with " +
                                          ModuleDescriptor.Requires.Modifier.SYNTHETIC +
                                          " modifier in module " +
                                          moduleDescriptor.name());
        }
        toSubstitute.add(req);
      }
      else {
        builder.requires(req);
      }
    }
    if (!toSubstitute.isEmpty()) {
      // voluntarily explicit, do not try to refactor it to make it look "pretty"
      var allStatic = toSubstitute.stream().allMatch((req) -> req.modifiers().contains(ModuleDescriptor.Requires.Modifier.STATIC));
      var atLeastOneTransitive =
        toSubstitute.stream().anyMatch((req) -> req.modifiers().contains(ModuleDescriptor.Requires.Modifier.TRANSITIVE));
      var modifiers = new HashSet<ModuleDescriptor.Requires.Modifier>();
      if (allStatic) {
        modifiers.add(ModuleDescriptor.Requires.Modifier.STATIC);
      }
      if (atLeastOneTransitive) {
        modifiers.add(ModuleDescriptor.Requires.Modifier.TRANSITIVE);
      }
      builder.requires(modifiers, fleetRuntimeModuleName);
    }

    for (var open : moduleDescriptor.opens()) {
      var targets =
        open.targets().stream().map((t) -> kotlinRuntimeModuleNames.contains(t) ? fleetRuntimeModuleName : t).collect(Collectors.toSet());
      if (targets.isEmpty()) {
        builder.opens(open.modifiers(), open.source());
      }
      else {
        builder.opens(open.modifiers(), open.source(), targets);
      }
    }
    for (var export : moduleDescriptor.exports()) {
      var filteredTargets = export.targets().stream().filter((t) -> !kotlinRuntimeModuleNames.contains(t)).collect(Collectors.toSet());
      if (filteredTargets.size() != export.targets().size()) { // if at least one needs to be substituted
        filteredTargets.add(fleetRuntimeModuleName);
        builder.exports(export.modifiers(), export.source(), filteredTargets);
      }
      else {
        builder.exports(export);
      }
    }
    builder.packages(moduleDescriptor.packages());
    return builder.build();
  }

  // TODO: this is very similar to [substituteModules] and to [UnqualifyExports#unqualifyExports], could we merge them?
  private static void fillBuilderFromModule(@NotNull ModuleDescriptor.Builder builder,
                                            @NotNull Set<String> requiresToIgnore,
                                            @NotNull ModuleDescriptor descriptor,
                                            @NotNull Set<String> alreadyAddedRequires) {
    descriptor.requires().stream().filter(x -> !requiresToIgnore.contains(x.name())).forEach(req -> {
      if (alreadyAddedRequires.add(req.name())) {
        builder.requires(req);
      }
    });
    descriptor.exports().forEach(builder::exports);
    descriptor.opens().forEach(builder::opens);
    descriptor.uses().forEach(builder::uses);
    descriptor.provides().forEach(builder::provides);
    descriptor.version().ifPresent(builder::version);
    descriptor.mainClass().ifPresent(builder::mainClass);
    builder.packages(descriptor.packages());
  }
}
