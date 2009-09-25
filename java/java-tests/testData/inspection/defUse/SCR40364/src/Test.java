package spike;

public class A
{
	public static void main (String[] args)
	{
		boolean thereIsMoreToParse;
		do {
			thereIsMoreToParse = buildNextObject ();
		} while (thereIsMoreToParse);
	}

	private static boolean buildNextObject () {
		return false;
	}
}
