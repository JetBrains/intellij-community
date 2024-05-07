package com.haulmont.yarg.loaders.factory;

import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">exception</info>.<info descr="Not resolved until the project is fully loaded">UnsupportedLoaderException</info>;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">loaders</info>.<info descr="Not resolved until the project is fully loaded">ReportDataLoader</info>;

import java.util.HashMap;
import java.util.Map;

public class DefaultLoaderFactory implements <info descr="Not resolved until the project is fully loaded">ReportLoaderFactory</info> {
    public static final String GROOVY_DATA_LOADER = "groovy";
    public static final String SQL_DATA_LOADER = "sql";
    public static final String JSON_DATA_LOADER = "json";

    protected Map<String, <info descr="Not resolved until the project is fully loaded">ReportDataLoader</info>> dataLoaders = new HashMap<String, <info descr="Not resolved until the project is fully loaded">ReportDataLoader</info>>();

    public DefaultLoaderFactory setDataLoaders(Map<String, <info descr="Not resolved until the project is fully loaded">ReportDataLoader</info>> dataLoaders) {
        this.dataLoaders.putAll(dataLoaders);
        return this;
    }

    public Map<String, <info descr="Not resolved until the project is fully loaded">ReportDataLoader</info>> getDataLoaders() {
        return dataLoaders;
    }

    public DefaultLoaderFactory setGroovyDataLoader(<info descr="Not resolved until the project is fully loaded">ReportDataLoader</info> dataLoader) {
        return registerDataLoader(GROOVY_DATA_LOADER, dataLoader);
    }

    public DefaultLoaderFactory setSqlDataLoader(<info descr="Not resolved until the project is fully loaded">ReportDataLoader</info> dataLoader) {
        return registerDataLoader(SQL_DATA_LOADER, dataLoader);
    }

    public DefaultLoaderFactory setJsonDataLoader(<info descr="Not resolved until the project is fully loaded">ReportDataLoader</info> dataLoader) {
        return registerDataLoader(JSON_DATA_LOADER, dataLoader);
    }

    public DefaultLoaderFactory registerDataLoader(String key, <info descr="Not resolved until the project is fully loaded">ReportDataLoader</info> dataLoader) {
        dataLoaders.put(key, dataLoader);
        return this;
    }

    @Override
    public <info descr="Not resolved until the project is fully loaded">ReportDataLoader</info> createDataLoader(String loaderType) {
        <info descr="Not resolved until the project is fully loaded">ReportDataLoader</info> dataLoader = dataLoaders.get(loaderType);
        if (dataLoader == null) {
            throw new <info descr="Not resolved until the project is fully loaded">UnsupportedLoaderException</info>(String.format("Unsupported loader type [%s]", loaderType));
        } else {
            return dataLoader;
        }
    }
}