package org.example;

public class Formatter {
    @  org .  jspecify  .annotations  .  NonNull
    <T, V> List<T> getNotNullList() {
        return List.of();
    }

    @org. jspecify. annotations   .
      Nullable
    <T, V> List<T> getNullableList() {
        return null;
    }

    @  org. jspecify  .  annotations.  NonNull
    String getNotNullName() {
        return "";
    }

    @  org .  jspecify .    annotations .  Nullable
    String getNullableName() {
        return null;
    }
}
