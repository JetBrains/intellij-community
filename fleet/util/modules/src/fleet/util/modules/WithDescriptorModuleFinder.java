package fleet.util.modules;

import com.intellij.util.lang.ImmutableZipFile;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class WithDescriptorModuleFinder implements ModuleFinder {
  private final Map<String, ModuleInfo.WithDescriptor> map;
  private final FleetModuleFinderLogger logger;

  WithDescriptorModuleFinder(Map<String, ModuleInfo.WithDescriptor> map, FleetModuleFinderLogger logger) {
    this.map = map;
    this.logger = logger;
  }

  static WithDescriptorModuleFinder build(Stream<ModuleInfo.WithDescriptor> withDescriptors, FleetModuleFinderLogger logger) {
    var map = withDescriptors.collect(Collectors.toMap(x -> x.descriptor().name(), x -> x));
    return new WithDescriptorModuleFinder(map, logger);
  }

  @Override
  public Optional<ModuleReference> find(String name) {
    return Optional.ofNullable(doFind(name));
  }

  @Override
  public Set<ModuleReference> findAll() {
    return this.map.keySet().stream()
      .map((k) -> doFind(k))
      .collect(Collectors.toSet());
  }

  private ModuleReference doFind(String name) {
    var moduleInfo = this.map.get(name);
    if (moduleInfo == null) {
      return null;
    }
    var file = new File(moduleInfo.path());
    if (DebugProbesHack.kotlinRuntimeModuleNames.contains(name)) {
      return DebugProbesHack.buildFleetKotlinRuntimeModuleReference(map, logger);
    }
    else {
      var substitutedDescriptor = DebugProbesHack.substituteModules(moduleInfo.descriptor());
      return singleModuleReference(substitutedDescriptor, file, file.toURI(), logger);
    }
  }

  private static ModuleReference singleModuleReference(ModuleDescriptor descriptor,
                                                       File file,
                                                       URI location,
                                                       FleetModuleFinderLogger logger) {
    if (file.isDirectory()) {
      return new FsModuleReference(descriptor, location);
    }
    else {
      return new JarModuleReference(descriptor, location, logger);
    }
  }

  static ModuleReference compositeJarModuleReference(
    ModuleDescriptor compositeModuleDescriptor,
    FleetModuleFinderLogger logger,
    ModuleInfo.WithDescriptor... modules
  ) {
    return new ModuleReference(compositeModuleDescriptor, null) {
      @Override
      public ModuleReader open() throws IOException {
        ModuleReader[] references = new ModuleReader[modules.length];
        for (int i = 0; i < modules.length; i++) {
          var file = new File(modules[i].path());
          references[i] = singleModuleReference(modules[i].descriptor(), file, file.toURI(), logger).open();
        }
        return new CompositeModuleReader(references);
      }
    };
  }

  private static class JarModuleReference extends ModuleReference {

    private final URI location;
    private final FleetModuleFinderLogger logger;

    protected JarModuleReference(ModuleDescriptor descriptor, URI location, FleetModuleFinderLogger logger) {
      super(descriptor, location);
      this.location = location;
      this.logger = logger;
    }

    @Override
    public ModuleReader open() throws IOException {
      var startTime = ClassLoadingStats.recordLoadingTime ? System.nanoTime() : -1;
      var zf = ImmutableZipFile.load(Path.of(location));
      if (startTime != -1) {
        var time = System.nanoTime() - startTime;
        ClassLoadingStats.open.time.add(time);
        ClassLoadingStats.open.counter.increment();
      }
      return new FleetModuleReader(zf, logger, location);
    }
  }

  private static class FsModuleReference extends ModuleReference {
    private final URI location;

    protected FsModuleReference(ModuleDescriptor descriptor, URI location) {
      super(descriptor, location);
      this.location = location;
    }

    @Override
    public ModuleReader open() {
      return new FleetFileSystemModuleReader(Path.of(location));
    }
  }
}