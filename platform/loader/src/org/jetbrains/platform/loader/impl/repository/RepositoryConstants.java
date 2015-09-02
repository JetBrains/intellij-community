/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.platform.loader.impl.repository;

/**
 * @author nik
 */
public class RepositoryConstants {
  public static final int VERSION_NUMBER = 5;
  public static final String CHECK_DEVELOPMENT_REPOSITORY_UP_TO_DATE_PROPERTY = "idea.loader.check.dev.repository";
  public static final String VERSION_FILE_NAME = "version.txt";
  public static final String MODULE_DESCRIPTORS_DIR_NAME = "module-descriptors";
  public static final String MODULES_ZIP_NAME = "modules.xml.zip";
  public static final String PROJECT_CONFIGURATION_HASH_FILE_NAME = "dev-project-configuration-hash.txt";
  public static final String GENERATED_BY_COMPILER_HASH_MARK = "compiled";
}
