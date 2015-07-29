@interface A1 {
    String <error descr="@interface member clashes with 'toString()' in java.lang.Object">toString</error>();
    Class <error descr="@interface member clashes with 'annotationType()' in java.lang.annotation.Annotation">annotationType</error>();
    int value();
    boolean equals();
    <error descr="Invalid type 'void' for annotation member">void</error> <error descr="@interface member clashes with 'finalize()' in java.lang.Object">finalize</error>();
    <error descr="Invalid type 'void' for annotation member">void</error> registerNatives();
}

