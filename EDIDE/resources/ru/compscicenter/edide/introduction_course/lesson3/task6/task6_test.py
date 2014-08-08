from test_helper import run_common_tests, import_file, passed, failed


def test_value(path):
    file = import_file(path)
    if file.contains:
        passed()
    failed("Use 'in' operator for this check")


if __name__ == '__main__':
    run_common_tests('''ice_cream = "ice cream"
contains = type here
print(contains)
''', '''ice_cream = "ice cream"
contains =
print(contains)
''', "You should modify the file")

    import sys
    path = sys.argv[-1]
    test_value(path)
# TODO : check it's not hard coded