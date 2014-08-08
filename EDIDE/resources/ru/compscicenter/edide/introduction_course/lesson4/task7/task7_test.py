from test_helper import run_common_tests

if __name__ == '__main__':
    run_common_tests('''grocery_list = ["fish", "tomato", 'apples']

print("tomato" in grocery_list)

grocery_dict = {"fish": 1, "tomato": 6, 'apples': 3}

print(is "fish" in grocery_dict keys)''',
                     '''grocery_list = ["fish", "tomato", 'apples']

print("tomato" in grocery_list)

grocery_dict = {"fish": 1, "tomato": 6, 'apples': 3}

print(is "fish" in grocery_dict keys)''', "Use in keyword")
