from test_helper import run_common_tests, get_task_windows, passed, failed


def test_window():
    window = get_task_windows()[0]
    if "a" in window:
        passed()
    else:
        failed("Use 'a' modifier to append lines to the end of file")


def test_window1():
    window = get_task_windows()[1]
    if "write" in window:
        passed()
    else:
        failed("Use 'write' method")


def test_window3():
    window = get_task_windows()[2]
    if "f" in window and "close" in window and "(" in window:
        passed()
    else:
        failed("Call 'close' method")

if __name__ == '__main__':
    run_common_tests()
    test_window()
    test_window1()
    test_window3()