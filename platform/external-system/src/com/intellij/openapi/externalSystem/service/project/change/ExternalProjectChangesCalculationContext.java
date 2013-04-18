package com.intellij.openapi.externalSystem.service.project.change;

import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChange;
import com.intellij.openapi.externalSystem.service.project.ExternalLibraryPathTypeMapper;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * <code>'Parameter object'</code> to use during project structure changes calculations.
 * <p/>
 * Not thread-safe.
 */
public class ExternalProjectChangesCalculationContext {

  @NotNull private final Set<ExternalProjectStructureChange> myKnownChanges   = ContainerUtilRt.newHashSet();
  @NotNull private final Set<ExternalProjectStructureChange> myCurrentChanges = ContainerUtilRt.newHashSet();

  @NotNull private final PlatformFacade                myPlatformFacade;
  @NotNull private final ExternalLibraryPathTypeMapper myLibraryPathTypeMapper;

  /**
   * @param knownChanges    changes between the gradle and ide project structure that has been known up until now
   * @param platformFacade  platform facade to use during the calculations
   * @param mapper          library path type mapper to use during the calculation
   */
  public ExternalProjectChangesCalculationContext(@NotNull Set<ExternalProjectStructureChange> knownChanges,
                                                  @NotNull PlatformFacade platformFacade,
                                                  @NotNull ExternalLibraryPathTypeMapper mapper)
  {
    myLibraryPathTypeMapper = mapper;
    myKnownChanges.addAll(knownChanges);
    myPlatformFacade = platformFacade;
  }
  
  @NotNull
  public Set<ExternalProjectStructureChange> getKnownChanges() {
    return myKnownChanges;
  }

  @NotNull
  public Set<ExternalProjectStructureChange> getCurrentChanges() {
    return myCurrentChanges;
  }
  
  public void register(@NotNull ExternalProjectStructureChange change) {
    myCurrentChanges.add(change);
  }

  public boolean hasNewChanges() {
    return !myKnownChanges.equals(myCurrentChanges);
  }
  
  @NotNull
  public PlatformFacade getPlatformFacade() {
    return myPlatformFacade;
  }

  @NotNull
  public ExternalLibraryPathTypeMapper getLibraryPathTypeMapper() {
    return myLibraryPathTypeMapper;
  }
}
