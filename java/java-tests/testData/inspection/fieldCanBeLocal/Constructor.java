class Temp {
    private int <warning descr="Field can be converted to a local variable">length</warning> = 10;
    private int[] array;

    public Temp()
    {
        array = new int[length];
    }


    public int[] getArray()
    {
        return array;
    }
}