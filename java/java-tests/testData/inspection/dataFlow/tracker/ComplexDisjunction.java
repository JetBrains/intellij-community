/*
Value is always true (counter == 0 || batches.hasNext() || pending.isEmpty(); line#39)
  One of the following happens:
    Operand #1 of or-chain is true (counter == 0; line#39)
      Left operand is 0 (counter; line#39)
        Range is known from line #33 (counter == 0; line#33)
    or operand #2 of or-chain is true (batches.hasNext(); line#39)
      One of the following happens:
        'batches.hasNext == true' was established from condition (counter == 0; line#39)
        or 'batches.hasNext == true' was established from condition (counter > 0 && batches.hasNext(); line#25)
    or operand #3 of or-chain is true (pending.isEmpty(); line#39)
      According to hard-coded contract, method 'isEmpty' returns 'true' value when this.size() == 0 (isEmpty; line#39)
 */
import java.util.Iterator;
import java.util.List;

abstract class IDEA250141
{
  abstract int returnSomeValue();

  abstract void updatePending(final List<String> pending);

  public void testMethod(final Iterator<List<String>> batches, final List<String> pending, int counter)
  {
    while (counter > 0 && batches.hasNext())
    {
      pending.addAll(batches.next());

      while (pending.size() > 10 || !pending.isEmpty() && !batches.hasNext())
      {
        counter -= returnSomeValue();

        if (counter == 0)
          break;

        updatePending(pending);
      }

      assert <selection>counter == 0 || batches.hasNext() || pending.isEmpty()</selection>;
    }
  }
}