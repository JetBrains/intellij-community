// "Make 'User' implement 'Parent'" "true-preview"
sealed interface Parent permits User {}

record User(int age) implements Parent {}