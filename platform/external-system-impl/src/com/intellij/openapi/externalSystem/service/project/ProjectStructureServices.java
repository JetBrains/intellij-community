package com.intellij.openapi.externalSystem.service.project;

import org.jetbrains.annotations.NotNull;

/**
 * Facades all services necessary for the 'sync project changes' processing.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/14/12 1:26 PM
 */
public class ProjectStructureServices {

  @NotNull private final ProjectStructureHelper        myProjectStructureHelper;
  @NotNull private final PlatformFacade                myPlatformFacade;
  @NotNull private final ExternalLibraryPathTypeMapper myLibraryPathTypeMapper;

  public ProjectStructureServices(@NotNull ProjectStructureHelper projectStructureHelper,
                                  @NotNull PlatformFacade platformFacade,
                                  @NotNull ExternalLibraryPathTypeMapper mapper)
  {
    myProjectStructureHelper = projectStructureHelper;
    myPlatformFacade = platformFacade;
    myLibraryPathTypeMapper = mapper;
  }

  @NotNull
  public ProjectStructureHelper getProjectStructureHelper() {
    return myProjectStructureHelper;
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
