// "Implement 'Parent'" "true"
sealed interface Parent permits User {}

record User(int age) implements Parent {}