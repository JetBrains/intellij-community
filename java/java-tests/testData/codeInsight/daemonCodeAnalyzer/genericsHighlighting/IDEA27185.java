import java.io.Serializable;
interface A<T extends Serializable>
{
    <S extends T> S foo(S s);
}
class B implements A<Number>
{
    @Override
    public <S extends Number> S foo(S s)
    {
        return s;
    }
}