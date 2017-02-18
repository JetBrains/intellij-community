// "Make 'T' extend 'java.lang.Enum'" "true"

class EnumGeneric<T extends Enum<?>> {
}

class WithEnum<T extends Enum<?>> extends EnumGeneric<T> {
}