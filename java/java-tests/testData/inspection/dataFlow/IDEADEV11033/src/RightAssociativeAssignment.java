public class NonconstantCondition {
	void test(boolean flag) {
		if (flag == (flag = true)) {
		}
	}
}