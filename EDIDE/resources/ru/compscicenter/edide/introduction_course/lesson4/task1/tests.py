from test_helper import run_common_tests, get_task_windows, passed, failed


def test_value():
    window = get_task_windows()[0]
    if "squares" in window and "[" in window and "]" in window and ":" in window:
        passed()
    else:
        failed("Use list slicing lst[index1:index2]")

if __name__ == '__main__':
    test_value()
    run_common_tests("Use list slicing lst[index1:index2]")
