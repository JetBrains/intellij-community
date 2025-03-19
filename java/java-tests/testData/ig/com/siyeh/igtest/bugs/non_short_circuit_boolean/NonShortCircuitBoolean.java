package com.siyeh.igtest.bugs.non_short_circuit_boolean;

public class NonShortCircuitBoolean
{
    private boolean m_bar = true;
    private boolean m_baz = false;
    private boolean m_foo = true;

    public NonShortCircuitBoolean()
    {
        final boolean nonShortAnd = <warning descr="Non-short-circuit boolean expression 'm_bar & m_baz & m_foo'">m_bar & m_baz & m_foo</warning>;
        final boolean nonShortOr = <warning descr="Non-short-circuit boolean expression 'm_bar | m_baz'">m_bar | m_baz</warning>;
        <warning descr="Non-short-circuit boolean expression 'm_bar |= m_baz'">m_bar |= m_baz</warning>;
        <warning descr="Non-short-circuit boolean expression 'm_bar &= m_baz'">m_bar &= m_baz</warning>;
    }

}
