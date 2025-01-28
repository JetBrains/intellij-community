/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.database.extensions;

import com.intellij.openapi.project.Project;

import java.util.List;

public final class DataExtractorBindings {
  public static final Binding<Project>                    PROJECT = new Binding<>("PROJECT");
  public static final Binding<List<? extends DataColumn>> ALL_COLUMNS = new Binding<>("ALL_COLUMNS");
  public static final Binding<List<? extends DataColumn>> COLUMNS = new Binding<>("COLUMNS");
  public static final Binding<ValueFormatter>             FORMATTER = new Binding<>("FORMATTER");
  public static final Binding<Appendable>                 OUT = new Binding<>("OUT");
  public static final Binding<DataStream<?>>              ROWS = new Binding<>("ROWS");
  public static final Binding<Boolean>                    TRANSPOSED = new Binding<>("TRANSPOSED");
  public static final Binding<Object>                     DATABASE_DIALECT = new Binding<>("DIALECT");
  public static final Binding<Object>                     DATABASE_TABLE = new Binding<>("TABLE");
}
