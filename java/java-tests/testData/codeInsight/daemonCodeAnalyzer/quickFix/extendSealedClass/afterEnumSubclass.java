// "Implement 'Parent'" "true"
sealed interface Parent permits MyEnum {}

enum MyEnum implements Parent {}