class ContainerClass
{
    private class SuperClass
    {
        private int field;
    }

    private class SubClass extends SuperClass
    {
        private SubClass()
        {
            System.out.println(super.field);
        }
    }
}