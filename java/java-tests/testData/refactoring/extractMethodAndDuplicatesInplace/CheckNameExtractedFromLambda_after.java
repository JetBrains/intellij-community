package org.example;

import java.io.File;
import java.util.Collection;

class Parent {
    public boolean test(Collection<File> files) {
        return false;
    }
}

class Child extends Parent {

  @Override
    public boolean test(Collection<File> files) {
        return files.stream().allMatch(file -> {
            String name = file.getName();
            return isBoolean(name);
        });
    }

    private static boolean isBoolean(String name) {
        return name.endsWith("jar") || name.endsWith("tar");
    }
}