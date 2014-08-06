from test_helper import run_common_tests, import_file


def test_value(path):
    file = import_file(path)
    if file.contains:
        return "#study_plugin test OK"
    return "#study_plugin Use 'in' operator for this check."


if __name__ == '__main__':
    run_common_tests('''ice_cream = "ice cream"
contains = type here
print(contains)
''', '''ice_cream = "ice cream"
contains =
print(contains)
''', "You should modify the file")

    # TODO: get filepath. Let's now assume that we pass it as the last item in command-line
    import sys
    path = sys.argv[-1]
    print(test_value(path))
