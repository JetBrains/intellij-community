/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.config;

/**
 * The configuration is stored standard xmlb.XmlSerializer that automatically stores the
 * state of this classes public fields.
 */
public class Config {

    public boolean useFullyQualifiedName = false;
    public InsertWhere insertNewMethodOption = InsertWhere.AT_CARET;
    public DuplicationPolicy whenDuplicatesOption = DuplicationPolicy.ASK;
    public boolean filterConstantField = true;
    public boolean filterEnumField = false;
    public boolean filterTransientModifier = false;
    public boolean filterStaticModifier = true;
    public String filterFieldName = null;
    public String filterMethodName = null;
    public String filterMethodType = null;
    public String filterFieldType = null;
    public boolean filterLoggers = true;
    public boolean addImplementSerializable = false;
    public boolean enableMethods = false;
    public boolean jumpToMethod = true; // jump cursor to toString method
    public int sortElements = 0; // 0 = none, 1 = asc, 2 = desc

    private FilterPattern myPattern = null;

    public boolean isUseFullyQualifiedName() {
        return useFullyQualifiedName;
    }

    public void setUseFullyQualifiedName(boolean useFullyQualifiedName) {
        this.useFullyQualifiedName = useFullyQualifiedName;
    }

    public DuplicationPolicy getReplaceDialogInitialOption() {
        return whenDuplicatesOption;
    }

    public void setReplaceDialogInitialOption(DuplicationPolicy option) {
        this.whenDuplicatesOption = option;
    }

    public InsertWhere getInsertNewMethodInitialOption() {
        return this.insertNewMethodOption;
    }

    public void setInsertNewMethodInitialOption(InsertWhere where) {
        this.insertNewMethodOption = where;
    }

    public boolean isFilterConstantField() {
        return filterConstantField;
    }

    public void setFilterConstantField(boolean filterConstantField) {
        this.filterConstantField = filterConstantField;
    }

    public boolean isFilterTransientModifier() {
        return filterTransientModifier;
    }

    public void setFilterTransientModifier(boolean filterTransientModifier) {
        this.filterTransientModifier = filterTransientModifier;
    }

    public boolean isFilterStaticModifier() {
        return filterStaticModifier;
    }

    public void setFilterStaticModifier(boolean filterStaticModifier) {
        this.filterStaticModifier = filterStaticModifier;
    }

    public String getFilterFieldName() {
        if (filterFieldName == null) {
            return "";
        }
        return filterFieldName;
    }

    public void setFilterFieldName(String filterFieldName) {
        this.filterFieldName = filterFieldName;
    }

    public boolean isEnableMethods() {
        return enableMethods;
    }

    public void setEnableMethods(boolean enableMethods) {
        this.enableMethods = enableMethods;
    }

    public String getFilterMethodName() {
        if (filterMethodName == null) {
            return "";
        }
        return filterMethodName;
    }

    public void setFilterMethodName(String filterMethodName) {
        this.filterMethodName = filterMethodName;
    }

    public boolean isJumpToMethod() {
        return jumpToMethod;
    }

    public void setJumpToMethod(boolean jumpToMethod) {
        this.jumpToMethod = jumpToMethod;
    }

    public boolean isFilterEnumField() {
        return filterEnumField;
    }

    public void setFilterEnumField(boolean filterEnumField) {
        this.filterEnumField = filterEnumField;
    }

    public int getSortElements() {
        return sortElements;
    }

    public void setSortElements(int sortElements) {
        this.sortElements = sortElements;
    }

    public String getFilterFieldType() {
        if (filterFieldType == null) {
            return "";
        }
        return filterFieldType;
    }

    public void setFilterFieldType(String filterFieldType) {
        this.filterFieldType = filterFieldType;
    }

    public boolean isFilterLoggers() {
        return filterLoggers;
    }

    public void setFilterLoggers(boolean filterLoggers) {
        this.filterLoggers = filterLoggers;
    }

    public String getFilterMethodType() {
        if (filterMethodType == null) {
            return "";
        }
        return filterMethodType;
    }

    public void setFilterMethodType(String filterMethodType) {
        this.filterMethodType = filterMethodType;
    }

    /**
     * Gets the filter pattern that this configuration represents.
     *
     * @return the filter pattern.
     */
    public FilterPattern getFilterPattern() {
        FilterPattern pattern = myPattern;
        if (pattern != null) {
            return pattern;
        }
        pattern = new FilterPattern();
        pattern.setConstantField(filterConstantField);
        pattern.setTransientModifier(filterTransientModifier);
        pattern.setStaticModifier(filterStaticModifier);
        pattern.setFieldName(filterFieldName);
        pattern.setFieldType(filterFieldType);
        pattern.setMethodName(filterMethodName);
        pattern.setMethodType(filterMethodType);
        pattern.setEnumField(filterEnumField);
        pattern.setLoggers(filterLoggers);
        return myPattern = pattern;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Config config = (Config) o;

        if (addImplementSerializable != config.addImplementSerializable) return false;
        if (enableMethods != config.enableMethods) return false;
        if (filterConstantField != config.filterConstantField) return false;
        if (filterEnumField != config.filterEnumField) return false;
        if (filterStaticModifier != config.filterStaticModifier) return false;
        if (filterTransientModifier != config.filterTransientModifier) return false;
        if (jumpToMethod != config.jumpToMethod) return false;
        if (sortElements != config.sortElements) return false;
        if (useFullyQualifiedName != config.useFullyQualifiedName) return false;
        if (filterFieldName != null ? !filterFieldName.equals(config.filterFieldName) : config.filterFieldName != null)
            return false;
        if (filterFieldType != null ? !filterFieldType.equals(config.filterFieldType) : config.filterFieldType != null)
            return false;
        if (filterMethodName != null ? !filterMethodName.equals(config.filterMethodName) : config.filterMethodName != null)
            return false;
        if (filterMethodType != null ? !filterMethodType.equals(config.filterMethodType) : config.filterMethodType != null)
            return false;
        if (!whenDuplicatesOption.equals(config.whenDuplicatesOption)) return false;
        if (!insertNewMethodOption.equals(config.insertNewMethodOption)) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = (useFullyQualifiedName ? 1 : 0);
        result = 29 * result + whenDuplicatesOption.hashCode();
        result = 29 * result + insertNewMethodOption.hashCode();
        result = 29 * result + (filterConstantField ? 1 : 0);
        result = 29 * result + (filterEnumField ? 1 : 0);
        result = 29 * result + (filterTransientModifier ? 1 : 0);
        result = 29 * result + (filterStaticModifier ? 1 : 0);
        result = 29 * result + (filterFieldName != null ? filterFieldName.hashCode() : 0);
        result = 29 * result + (filterFieldType != null ? filterFieldType.hashCode() : 0);
        result = 29 * result + (filterMethodName != null ? filterMethodName.hashCode() : 0);
        result = 29 * result + (filterMethodType != null ? filterMethodType.hashCode() : 0);
        result = 29 * result + (addImplementSerializable ? 1 : 0);
        result = 29 * result + (enableMethods ? 1 : 0);
        result = 29 * result + (jumpToMethod ? 1 : 0);
        result = 29 * result + sortElements;
        return result;
    }

    public String toString() {
        return "Config{" +
                "useFullyQualifiedName=" + useFullyQualifiedName +
                ", replaceDialogOption=" + whenDuplicatesOption +
                ", insertNewMethodOption=" + insertNewMethodOption +
                ", filterConstantField=" + filterConstantField +
                ", filterEnumField=" + filterEnumField +
                ", filterTransientModifier=" + filterTransientModifier +
                ", filterStaticModifier=" + filterStaticModifier +
                ", filterFieldName='" + filterFieldName + "'" +
                ", filterFieldType='" + filterFieldType + "'" +
                ", filterMethodName='" + filterMethodName + "'" +
                ", filterMethodType='" + filterMethodType + "'" +
                ", addImplementSerializable=" + addImplementSerializable +
                ", enableMethods=" + enableMethods +
                ", jumpToMethod=" + jumpToMethod +
                ", sortElements=" + sortElements +
                "}";
    }

}