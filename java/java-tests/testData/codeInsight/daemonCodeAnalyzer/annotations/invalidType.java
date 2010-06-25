class Clazz {}

@interface Ann {
    <error descr="Invalid type for annotation member">Clazz</error> i ();
    <error descr="Cyclic annotation element type">Ann</error> j ();

    <error descr="Invalid type for annotation member">void</error> f(); 
}
