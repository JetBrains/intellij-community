class Clazz {}

@interface Ann {
    <error descr="Invalid type 'Clazz' for annotation member">Clazz</error> i ();
    <error descr="Cyclic annotation element type">Ann</error> j ();

    <error descr="Invalid type 'void' for annotation member">void</error> f(); 
    <error descr="Invalid type 'int[][]' for annotation member">int[]</error> intDblArray()[];
}
