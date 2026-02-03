class IncrDecrIgnore {
	int <caret>i = <flown1>0;

	int incr() {
		return <flown2>i++;
	}
	int decr() {
		return <flown3>--i;
	}
}