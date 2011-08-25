class CastTest
{
    public static void main (String[] args) throws CloneNotSupportedException
    {
       int i = 0;
       synchronized ((Object)i){}
    }
}