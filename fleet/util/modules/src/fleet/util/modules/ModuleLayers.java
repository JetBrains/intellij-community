package fleet.util.modules;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ModuleLayers {
  public static ModuleDescriptor deserializeModuleDescriptor(String data) {
    return convertOpenToAutomatic(ModuleDescriptor.read(ByteBuffer.wrap((Base64.getDecoder().decode(data)))));
  }

  public static ModuleLayer moduleLayer(List<ModuleLayer> parentLayers,
                                        Collection<ModuleInfo> modulePath,
                                        FleetModuleFinderLogger logger) {
    var before = moduleFinder(modulePath, logger);
    var parentLayers1 = new ArrayList<>(parentLayers);
    parentLayers1.add(ModuleLayer.boot());
    var parents = parentLayers1.stream().map(x -> x.configuration()).toList();
    var after = ModuleFinder.of();

    var beforeFindAll = before.findAll();
    var rootModuleNames = beforeFindAll.stream().map(x -> x.descriptor().name()).collect(Collectors.toSet());
    var configuration = Configuration.resolve(before, parents, after, rootModuleNames);
    var parentClassLoader = ClassLoader.getSystemClassLoader();
    var controller = ModuleLayer.defineModulesWithOneLoader(configuration, parentLayers1, parentClassLoader);
    return controller.layer();
  }

  private static PatchedFleetModuleFinder moduleFinder(Collection<ModuleInfo> modulePath, FleetModuleFinderLogger logger) {
    var jarDescriptors = paths(modulePath.stream())
      .filter(x -> x.path().endsWith(".jar"))
      .map(x -> {
        var moduleReferences = ModuleFinder.of(Path.of((x).path())).findAll().stream().toList();
        if (moduleReferences.size() != 1) {
          throw new RuntimeException("expected single module reference");
        }
        var moduleReference = moduleReferences.get(0);
        return new ModuleInfo.WithDescriptor(moduleReference.descriptor(), x.path());
      });

    var jarFinder = WithDescriptorModuleFinder.build(jarDescriptors, logger);
    var descriptorsFinder = WithDescriptorModuleFinder.build(descriptors(modulePath.stream()), logger);
    var dirs = paths(modulePath.stream()).filter(x -> !x.path().endsWith(".jar"))
      .map(x -> Path.of(x.path()))
      .toArray(Path[]::new);
    if (dirs.length == 0) {
      return new PatchedFleetModuleFinder(new ModuleFinder[]{descriptorsFinder, jarFinder});
    }
    else {
      var dirFinder = ModuleFinder.of(dirs);
      return new PatchedFleetModuleFinder(new ModuleFinder[]{descriptorsFinder, jarFinder, dirFinder});
    }
  }

  private static Stream<ModuleInfo.WithDescriptor> descriptors(Stream<ModuleInfo> stream) {
    return stream
      .filter(x -> x instanceof ModuleInfo.WithDescriptor)
      .map(x -> (ModuleInfo.WithDescriptor)x);
  }

  private static Stream<ModuleInfo.Path> paths(Stream<ModuleInfo> stream) {
    return stream
      .filter(x -> x instanceof ModuleInfo.Path)
      .map(x -> (ModuleInfo.Path)x);
  }

  /**
   * Converts an open module to an automatic module.
   *
   * Automatic modules are not serializable as by definition they are not represented by a `module-info.class` in the build tooling of
   * Fleet we thus convert automatic modules to open modules to be able to serialize them into a base64-encoded `module-info.class`.
   * This code converts such a module back to an automatic module.
   *
   * This function must be aligned with its compile time equivalent [fleet.buildtool.codecache.automaticToOpen]
   */
  private static ModuleDescriptor convertOpenToAutomatic(ModuleDescriptor moduleDescriptor) {
    if (moduleDescriptor.isOpen()) {
      var builder = ModuleDescriptor.newAutomaticModule(moduleDescriptor.name());
      if (moduleDescriptor.version().isPresent()) {
        builder.version(moduleDescriptor.version().get());
      }
      for (var provide : moduleDescriptor.provides()) {
        builder.provides(provide);
      }
      builder.packages(moduleDescriptor.packages());
      return builder.build();
    }
    else {
      return moduleDescriptor;
    }
  }

  private static final class PatchedFleetModuleFinder implements ModuleFinder {

    private final ConcurrentHashMap<String, FleetModuleReference> nameToModule;
    private final ModuleFinder[] finders;
    private volatile Set<ModuleReference> all;

    PatchedFleetModuleFinder(ModuleFinder[] finders) {
      this.finders = finders;
      this.nameToModule = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<ModuleReference> find(String name) {
      return Optional.ofNullable(doFind(translatedName(name)));
    }

    @Override
    public Set<ModuleReference> findAll() {
      if (all == null) {
        synchronized (this) {
          if (all == null) {
            var result = new HashSet<ModuleReference>(nameToModule.size());
            result.addAll(nameToModule.values());
            for (ModuleFinder finder : finders) {
              for (ModuleReference ref : finder.findAll()) {
                var name = translatedName(ref.descriptor().name());
                var fleetRef = new FleetModuleReference(ref);
                if (nameToModule.putIfAbsent(name, fleetRef) == null) {
                  result.add(fleetRef);
                }
              }
            }
            all = result;
          }
        }
      }
      return all;
    }

    private static String translatedName(String name) {
      // User code could use reflection to look for `kotlinRuntimeModuleNames` modules, we need to return them
      // our `fleetRuntimeModuleName` module instead.
      return DebugProbesHack.kotlinRuntimeModuleNames.contains(name) ? DebugProbesHack.fleetRuntimeModuleName : name;
    }

    private FleetModuleReference doFind(String name) {
      var cached = nameToModule.computeIfAbsent(name, (k) -> {
        for (ModuleFinder finder : finders) {
          var found = finder.find(name);
          if (found.isPresent()) {
            return new FleetModuleReference(found.get());
          }
        }
        return FleetModuleReference.mockModuleReference;
      });
      return cached == FleetModuleReference.mockModuleReference ? null : cached;
    }

    private static class FleetModuleReference extends ModuleReference {
      static FleetModuleReference mockModuleReference = new FleetModuleReference(new DummyModuleReference());

      private final ModuleReference moduleReference;

      FleetModuleReference(ModuleReference moduleReference) {
        super(UnqualifyExports.unqualifyExports(moduleReference.descriptor()), moduleReference.location().orElse(null));
        this.moduleReference = moduleReference;
      }

      @Override
      public ModuleReader open() throws IOException {
        return moduleReference.open();
      }
    }

    private static class DummyModuleReference extends ModuleReference {
      DummyModuleReference() {
        super(ModuleDescriptor.newModule("com.example").build(), URI.create("dummy:module"));
      }

      @Override
      public ModuleReader open() {
        throw new RuntimeException("dummy");
      }
    }
  }
}