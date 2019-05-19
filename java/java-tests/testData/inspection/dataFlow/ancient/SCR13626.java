class Test
{
<error descr="Duplicate class: 'Test'">class Test</error>
{
    public void x()
    {
        boolean a = false;
        boolean b = true;

        do
        {
            a = true;
        }
        while( <warning descr="Condition '!a && b' is always 'false'"><warning descr="Condition '!a' is always 'false'">!a</warning> && b</warning> );
    }
}}
