from test_helper import run_common_tests, passed, failed, get_task_windows


def test_window():
    window = get_task_windows()[0]
    if "phone_book" in window and "values" in window:
        passed()
    else:
        failed("Use values() method")


if __name__ == '__main__':
    run_common_tests()
    test_window()
