class CastTest
{
    public static void main (String[] args) throws CloneNotSupportedException
    {
        CastTest ct1 = new CastTest ();
        // The cast of ct1 is obviously redundant (although the cast of the result is necessary)
        CastTest ct2 = (CastTest) ((CastTest)ct1).clone();
    }
}