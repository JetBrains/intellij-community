// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.inspectopedia.extractor.data;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class Plugins {

    public List<Plugin> plugins;

    public String ideCode;

    public String ideName;

    public String ideVersion;

    public Plugins(List<Plugin> plugins, String ideCode, String ideName, String ideVersion) {
        this.plugins = plugins;
        this.ideCode = ideCode;
        this.ideName = ideName;
        this.ideVersion = ideVersion;
    }

    public Plugins() {
    }

    @NotNull
    public List<Plugin> getPlugins() {
        return Optional.ofNullable(plugins)
                .map((Function<List<Plugin>, List<Plugin>>) ArrayList::new)
                .orElse(Collections.emptyList());
    }
}
