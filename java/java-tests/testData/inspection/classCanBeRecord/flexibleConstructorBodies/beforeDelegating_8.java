// "Convert to record class" "false"
interface LivingAndBreathing {
    default void performBackflip() {
        System.out.println("Watch out, I'm gonna do a backflip");
    }
}

class Person<caret> implements LivingAndBreathing {
    final String name;
    final int age;

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    Person(String name) {
        performBackflip(); // javac error: "cannot reference performBackflip() before supertype constructor has been called"
        this(name, 0);
    }
}