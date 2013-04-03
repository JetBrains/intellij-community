package com.intellij.openapi.externalSystem.model.project;

import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * We need to be able to perform cloning of external project entities. However, there is a possible case that particular entity
 * encapsulates graph of other entities. For example, {@link ExternalModule} has a number of
 * {@link ExternalModule#getDependencies() dependencies} where those dependencies can reference other modules that, in turn, also
 * have dependencies.
 * <p/>
 * The problem is that we need to ensure that particular entity is shared within a single entities graph (e.g. there should
 * be a single shared instance of {@link ExternalModule gradle module} after cloning). That's why we need some place to serve
 * as a cache during cloning. This class serves that purpose.
 * 
 * @author Denis Zhdanov
 * @since 9/28/11 12:36 PM
 */
public class ExternalEntityCloneContext {
  
  private final Map<ExternalLibrary, ExternalLibrary> myLibraries = new HashMap<ExternalLibrary, ExternalLibrary>();
  private final Map<ExternalModule, ExternalModule> myModules = new HashMap<ExternalModule, ExternalModule>();

  @Nullable
  public ExternalLibrary getLibrary(@NotNull ExternalLibrary library) {
    return myLibraries.get(library);
  }

  public void store(@NotNull ExternalLibrary key, @NotNull ExternalLibrary value) {
    myLibraries.put(key, value);
  }
  
  @Nullable
  public ExternalModule getModule(@NotNull ExternalModule module) {
    return myModules.get(module);
  }

  public void store(@NotNull ExternalModule key, @NotNull ExternalModule value) {
    myModules.put(key, value);
  }
}
