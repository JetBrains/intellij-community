// "Make 'T' extend 'java.lang.Enum'" "true-preview"

class EnumGeneric<T extends Enum<?>> {
}

class WithEnum<T> extends EnumGeneric<<caret>T> {
}